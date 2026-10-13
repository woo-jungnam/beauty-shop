"""Read-only exploratory temporal holdout; never invent scores without examples.

Uses the CURRENT catalog, not historical catalog snapshots. Results from a small
interaction log describe that snapshot only, not clinical relevance or general
production accuracy. No app startup, neural APIs or vector-store writes.
"""
import argparse
import importlib.util
import json
import math
from collections import defaultdict
from datetime import timezone
from pathlib import Path
import sys


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def metrics(predictions, targets, k):
    hits = [1 if pid in targets else 0 for pid in predictions[:k]]
    count = sum(hits)
    dcg = sum(hit / math.log2(rank + 2) for rank, hit in enumerate(hits))
    ideal = sum(1 / math.log2(rank + 2) for rank in range(min(k, len(targets))))
    return {'precision': count / k, 'recall': count / len(targets),
            'hit_rate': float(count > 0), 'ndcg': dcg / ideal if ideal else 0}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', default=str(Path(__file__).resolve().parents[1]))
    parser.add_argument('--k', type=int, default=8)
    args = parser.parse_args()
    if args.k < 1 or args.k > 50:
        raise ValueError('k must be 1..50')
    root = Path(args.root)
    sys.path.insert(0, str(root))
    core = load('content_core', root/'app/services/content_based.py')
    extraction = load('catalog_extractor', root/'app/services/ingestion/extractor.py')
    controls = load('content_controls', root/'app/services/recommendation_config.py')
    extractor = extraction.DatabaseExtractor()
    config = controls.RecommendationConfig(extractor).get(force=True)
    index = core.ContentIndex(extractor.extractProducts(), {name:config['field.'+name] for name in core.ContentIndex.FIELD_NAMES})
    conn = extractor.getConnection()
    try:
        with conn.cursor() as cur:
            cur.execute('START TRANSACTION READ ONLY')
            cur.execute('SELECT user_id,session_id,product_id,action_type,created_at,id FROM user_product_interactions ORDER BY created_at,id LIMIT 100000')
            rows = cur.fetchall()
        conn.rollback()
    finally:
        conn.close()
    groups = defaultdict(list)
    for row in rows:
        actor = ('user',row['user_id']) if row['user_id'] else ('session',row['session_id'])
        if actor[1]: groups[actor].append(row)
    outputs = {'pure_content_based':[], 'popular_baseline':[]}
    recommendations = defaultdict(set)
    skipped = 0
    eligible = {pid for pid,p in index.products.items() if not config['in_stock_only'] or p.get('stock',0)>0}
    weights = {'VIEW':config['weight.view'],'SEARCH_CLICK':config['weight.search_click'],
               'ADD_TO_CART':config['weight.add_to_cart'],'PURCHASE':config['weight.purchase']}
    for history in groups.values():
        if len(history) < 3:
            skipped += 1; continue
        split = max(1, min(len(history)-1,int(len(history)*0.8)))
        train, test = history[:split], history[split:]
        seen = {r['product_id'] for r in train}
        targets = {r['product_id'] for r in test if r['product_id'] not in seen and r['product_id'] in eligible}
        if not targets:
            skipped += 1; continue
        cutoff = train[-1]['created_at']
        cutoff = cutoff.replace(tzinfo=timezone.utc) if cutoff.tzinfo is None else cutoff
        profile, _, _ = index.profile(train[-config['history_window']:], weights, config['half_life_days'], config['max_product_weight'], cutoff)
        predictions = {
            'pure_content_based':[r['id'] for r in index.rank(profile,args.k,seen,config['min_similarity'],config['in_stock_only'])],
            'popular_baseline':[r['id'] for r in index.popular(args.k,seen,config['in_stock_only'])],
        }
        for name,pids in predictions.items():
            outputs[name].append(metrics(pids,targets,args.k))
            recommendations[name].update(pids)
    count = len(outputs['pure_content_based'])
    result = {'evaluation':'temporal_80_20_next_unseen_items_current_catalog', 'k':args.k,
              'events':len(rows),'actor_groups':len(groups),'evaluated_groups':count,'skipped_groups':skipped,
              'index_version':index.version,'eligible_products':len(eligible),
              'limitations':['Current catalog, not historical snapshots','Small observational sample; not a production accuracy guarantee'],
              'algorithms':{name:{key:sum(m[key] for m in values)/len(values) if values else None
                                 for key in ('precision','recall','hit_rate','ndcg')} |
                            {'catalog_coverage':len(recommendations[name])/len(eligible) if eligible else None}
                            for name,values in outputs.items()}}
    print(json.dumps(result,ensure_ascii=True,indent=2))


if __name__ == '__main__': main()
