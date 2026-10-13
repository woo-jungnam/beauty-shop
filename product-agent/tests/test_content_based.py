"""Deterministic mathematical/eligibility tests, without app startup or DB writes."""
import ast
import __future__
import importlib.util
import json
import math
import time
from collections import defaultdict
from datetime import datetime, timedelta, timezone
from pathlib import Path
from threading import RLock
from types import SimpleNamespace

import pytest

ROOT = Path(__file__).resolve().parents[1]


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, ROOT / path)
    obj = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(obj)
    return obj


content = module('pure_content', 'app/services/content_based.py')
controls = module('pure_controls', 'app/services/recommendation_config.py')


def product(pid, **values):
    result = dict(id=f'mysql_{pid}', target_type='PRODUCT', name='Product', category='serum',
                brand='Brand', description='', ingredients=[], skin_type=[], concerns=[],
                stock=10, total_sold=0, rating=0)
    result.update(values)
    return result


def row(pid, action='VIEW', age=0):
    return {'product_id': pid, 'action_type': action, 'created_at': datetime.now(timezone.utc) - timedelta(days=age)}


def test_idf_document_frequency_and_sublinear_tf():
    index = content.ContentIndex([product(1, mapped_ingredients=['niacinamide zinc']), product(2, mapped_ingredients=['niacinamide'])])
    assert index.idf[('ingredients', 'zinc')] == pytest.approx(math.log(3 / 2) + 1)
    assert index.idf[('ingredients', 'niacinamide')] == 1
    assert sum(v*v for v in index.vectors[1].values()) == pytest.approx(1)


def test_ingredients_affect_cosine_and_explanation():
    index = content.ContentIndex([product(1, mapped_ingredients=['retinol']), product(2, mapped_ingredients=['retinol']), product(3, mapped_ingredients=['glycerin'])])
    results = index.rank(index.vectors[1], 2, {1})
    assert [r['id'] for r in results] == [2, 3]
    assert results[0]['score'] > results[1]['score']
    assert 'ingredients' in results[0]['field_scores']


def test_one_vector_per_product_no_services_stock_or_self():
    items = [product(1), product(2, stock=0), product(3), dict(id='service_5', target_type='SERVICE')]
    index = content.ContentIndex(items)
    assert len(index.vectors) == 3
    assert [r['id'] for r in index.rank(index.vectors[1], 8, {1})] == [3]


def test_centroid_matches_independent_weighted_sum():
    index = content.ContentIndex([product(1, skin_type=['oily']), product(2, skin_type=['dry'])])
    actual, seen, matched = index.profile([row(1), row(2, 'ADD_TO_CART')], {'VIEW': 1, 'ADD_TO_CART': 3}, 0, 100)
    expected = defaultdict(float)
    for pid, weight in [(1, 1), (2, 3)]:
        for key, value in index.vectors[pid].items(): expected[key] += weight * value
    expected = content.normalize(expected)
    assert actual == pytest.approx(expected)
    assert seen == {1, 2} and matched == 2


def test_decay_is_half_after_half_life_and_repeats_are_capped():
    index = content.ContentIndex([product(1, skin_type=['oily']), product(2, skin_type=['dry'])])
    now = datetime.now(timezone.utc)
    old = [{'product_id':1,'action_type':'VIEW','created_at':now - timedelta(days=1)}, {'product_id':2,'action_type':'VIEW','created_at':now}]
    actual, _, _ = index.profile(old, {'VIEW': 1}, 1, 100, now)
    expected = content.normalize({key: 0.5*index.vectors[1].get(key,0)+index.vectors[2].get(key,0)
                                  for key in index.vectors[1].keys() | index.vectors[2].keys()})
    assert actual == pytest.approx(expected)
    a, _, _ = index.profile([row(1)]*20+[row(2)], {'VIEW': 1}, 0, 1)
    b, _, _ = index.profile([row(1),row(2)], {'VIEW': 1}, 0, 1)
    assert a == pytest.approx(b)


def test_invalid_zero_and_unknown_features_do_not_rank():
    index = content.ContentIndex([product(1)])
    assert index.rank({}, 8) == []
    assert index.rank({('bad','feature'):float('nan')}, 8) == []
    assert index.rank({('bad','feature'):1}, 8) == []
    assert index.rank(index.vectors[1], 8, minimum=1)[0]['score'] == 1


def test_index_replacement_removes_deleted_products():
    old = content.ContentIndex([product(1),product(2)])
    new = content.ContentIndex([product(1)])
    assert old.version != new.version and 2 not in new.vectors


def test_skin_and_formula_are_actual_separate_features():
    p = product(1, skin_type=['combination'], has_alcohol=False, has_fragrance=None)
    data = content.features(p)
    assert data['skin'] == 'skin_combination'
    assert 'has_alcohol_False' in data['formula']
    assert 'has_fragrance' not in data['formula']


def test_admin_compatibility_scores_change_the_content_vector():
    index = content.ContentIndex([product(1, skin_type=['oily'], skin_scores={'oily':0.2}),
                                 product(2, skin_type=['oily'], skin_scores={'oily':0.9})])
    assert index.vectors[1] != index.vectors[2]


def test_field_controls_change_ranking_without_mixing_spaces():
    rows=[product(1, mapped_ingredients=['retinol'], category='serum'),
          product(2, mapped_ingredients=['retinol'], category='cream'),
          product(3, mapped_ingredients=['glycerin'], category='serum')]
    ingredients={name:(1 if name=='ingredients' else 0) for name in content.FIELDS}
    category={name:(1 if name=='category' else 0) for name in content.FIELDS}
    a=content.ContentIndex(rows,ingredients); b=content.ContentIndex(rows,category)
    assert a.rank(a.vectors[1],1,{1})[0]['id']==2
    assert b.rank(b.vectors[1],1,{1})[0]['id']==3
    assert a.version != b.version


@pytest.mark.parametrize('values', [{'history_window':0}, {'history_window':1.5}, {'min_similarity':float('nan')},
    {'exclude_seen':'yes'}, {'weight.purchase':-1}, {'unknown':1}, {'catalog_refresh_seconds':4},
    {key:0 for key in ['weight.view','weight.purchase','weight.add_to_cart','weight.search_click']}])
def test_invalid_config_rejected(values):
    with pytest.raises((ValueError,TypeError)): controls.validate(values)


class Extractor:
    def __init__(self):
        self.products = [product(1, mapped_ingredients=['retinol']), product(2, mapped_ingredients=['retinol']), product(3, mapped_ingredients=['glycerin'])]
        self.history = []
        self.config = []
        self.sql = ''
        self.calls = 0

    def extractProducts(self):
        self.calls += 1
        return self.products

    def getConnection(self):
        parent = self
        class Cursor:
            def __enter__(self): return self
            def __exit__(self,*args): pass
            def execute(self,sql,params=None):
                assert sql.lstrip().upper().startswith('SELECT')
                parent.sql = sql
            def fetchall(self): return parent.config if 'system_configs' in parent.sql else parent.history
        return SimpleNamespace(cursor=lambda:Cursor(),close=lambda:None)


def service(extractor):
    tree = ast.parse((ROOT/'app/services/recommendation.py').read_text(encoding='utf-8'))
    definitions = [n for n in tree.body if isinstance(n,ast.ClassDef)]
    context = {'ContentIndex':content.ContentIndex,'RecommendationConfig':controls.RecommendationConfig,
               'time':time,'datetime':datetime,'timezone':timezone,'RLock':RLock,
               'logger':SimpleNamespace(exception=lambda *args:None)}
    exec(compile(ast.Module(body=definitions,type_ignores=[]),'recommendation.py','exec'),context)
    return context['RecommendationService'](extractor)


def test_source_is_cold_start_then_personalized_and_not_fake_centroid():
    db = Extractor(); engine = service(db)
    assert engine.for_user()['source'] == 'COLD_START'
    db.history = [row(1,'PURCHASE')]
    result = engine.for_user(session_id='fixture',limit=2)
    assert result['source'] == 'CONTENT_BASED'
    assert result['recommended_product_ids'][0] == 2
    assert 1 not in result['recommended_product_ids']
    assert all(r['score'] is not None for r in result['items'])
    assert 'user_id IS NULL' in db.sql


def test_cached_catalog_and_expiry_refresh():
    db = Extractor(); engine = service(db)
    engine.similar(1); engine.similar(1)
    assert db.calls == 1
    db.products = [product(1)]
    engine.expires = 0
    assert engine.similar(1)['recommended_product_ids'] == []
    assert db.calls == 2


def test_unavailable_catalog_does_not_use_stale_eligibility():
    db = Extractor(); engine = service(db); engine.similar(1)
    def fail(): raise RuntimeError('database unavailable')
    db.extractProducts = fail; engine.expires = 0
    assert engine.similar(1)['status'] == 'unavailable'
    assert engine.similar(1)['items'] == []


def test_admin_config_changes_take_effect_and_account_history_isolated():
    db = Extractor(); engine = service(db)
    db.products[1] = product(2, mapped_ingredients=['different'])
    db.config = [{'config_key':'recommendation.min_similarity','config_value':'1'}]
    assert engine.similar(1)['items'] == []
    engine.history(7,'fixture',100)
    assert 'OR (user_id IS NULL AND session_id=%s)' in db.sql


def extracted(rows, mappings=None, skins=None, concerns=None):
    tree = ast.parse((ROOT/'app/services/ingestion/extractor.py').read_text(encoding='utf-8'))
    nodes = [n for n in tree.body if isinstance(n,ast.ClassDef) or isinstance(n,ast.Assign)
             and any(isinstance(t,ast.Name) and t.id in ('CATEGORY_MAP','CONCERN_KEYWORDS') for t in n.targets)]
    context = {'json':json,'defaultdict':defaultdict}
    exec(compile(ast.Module(body=nodes,type_ignores=[]),'extractor.py','exec',flags=__future__.annotations.compiler_flag),context)
    current = {'sql':''}
    class Cursor:
        def __enter__(self): return self
        def __exit__(self,*args): pass
        def execute(self,sql,params=None): current['sql']=sql
        def fetchall(self):
            if 'FROM product_ingredients' in current['sql']: return mappings or []
            if 'FROM product_skin_compatibility' in current['sql']: return skins or []
            if 'FROM product_skin_concerns' in current['sql']: return concerns or []
            return rows
    obj = object.__new__(context['DatabaseExtractor'])
    obj.getConnection=lambda:SimpleNamespace(cursor=lambda:Cursor(),close=lambda:None)
    return obj.extractProducts()


def database_row(pid, skin):
    return dict(id=pid, name='Product', brand='Brand', category='Serum | Toner', price=0, stock=1,
                skin_type=skin, ingredients='', how_to_use='', short_description='acne', description='',
                rating=0, total_reviews=0, total_sold=0, image_url=None, is_featured=False,
                has_fragrance=None, has_alcohol=False)


def test_extraction_preserves_combination_normal_unknown_and_multiple_categories():
    rows=extracted([database_row(1,'COMBINATION'),database_row(2,'NORMAL'),database_row(3,None)])
    assert [r['skin_type'] for r in rows] == [['combination'],['normal'],[]]
    assert rows[0]['category_names'] == ['Serum','Toner']
    assert rows[0]['has_fragrance'] is None and rows[0]['has_alcohol'] is False


def test_structured_admin_data_overrides_keyword_inference_and_maps_inci():
    rows=extracted([database_row(1,'COMBINATION')],
        [{'product_id':1,'name':'Niacinamide','inci_name':'Niacinamide','functions':'["conditioning"]','benefits':'["hydration"]'}],
        [{'product_id':1,'code':'OILY','score':0.9,'is_recommended':True}, {'product_id':1,'code':'SENSITIVE','score':0.1,'is_recommended':False}],
        [{'product_id':1,'code':'HYPERPIGMENTATION','score':0.7}])
    assert rows[0]['skin_type'] == ['oily']
    assert rows[0]['not_ideal_skin_types'] == ['sensitive']
    assert rows[0]['concerns'] == ['hyperpigmentation']
    assert rows[0]['skin_scores']['oily'] == 0.9
    assert 'Niacinamide' in rows[0]['mapped_ingredients'][0]
