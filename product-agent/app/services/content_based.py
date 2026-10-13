"""Field-aware TF-IDF content model, independent of neural/RAG embeddings.

Sublinear TF, smoothed IDF, normalized field blocks and a normalized weighted
user centroid share one vocabulary. Ranking is exact cosine, never popularity.
Popularity is an explicitly labelled cold-start/backfill policy in the caller.
"""
import hashlib
import json
import math
import re
import unicodedata
from collections import Counter, defaultdict
from datetime import datetime, timezone

FIELDS = {'name': 1.0, 'category': 2.0, 'brand': 0.5, 'description': 1.0,
          'ingredients': 3.0, 'skin': 2.0, 'concerns': 2.0, 'formula': 0.5}
LABELS = {'name': 'tên sản phẩm', 'category': 'danh mục', 'brand': 'thương hiệu',
          'description': 'mô tả', 'ingredients': 'thành phần', 'skin': 'loại da',
          'concerns': 'nhu cầu da', 'formula': 'thông tin công thức'}
STOP = set('va la cua cho voi mot cac nhung duoc san pham giup trong khi tu den nay su dung the co de theo'.split())


def tokens(text):
    text = unicodedata.normalize('NFKD', str(text or '').casefold().replace('đ', 'd'))
    text = ''.join(c for c in text if not unicodedata.combining(c))
    words = [w for w in re.findall(r'[a-z0-9_]+', text) if len(w) > 1 and w not in STOP]
    return words + [f'{a}~{b}' for a, b in zip(words, words[1:])]


def normalize(vector):
    if not vector or any(not math.isfinite(v) for v in vector.values()):
        return {}
    norm = math.sqrt(sum(v * v for v in vector.values()))
    return {k: v / norm for k, v in vector.items()} if norm > 1e-12 else {}


def features(product):
    skin = product.get('skin_type', [])
    concerns = product.get('concerns', [])
    mapped = product.get('mapped_ingredients', [])
    ingredient_text = product.get('ingredients', [])
    if isinstance(ingredient_text, list):
        ingredient_text = ' '.join(ingredient_text)
    categories = product.get('category_names') or [product.get('category', '')]
    categories = [value for value in categories if value and value != 'Khác']
    brand = product.get('brand', '')
    brand = '' if brand == 'Khác' else brand
    return {
        'name': product.get('name', ''), 'category': ' '.join(categories),
        'brand': brand, 'description': product.get('description', ''),
        'ingredients': ' '.join([str(ingredient_text)] + [str(x) for x in mapped]),
        'skin': ' '.join(f'skin_{x}' for x in skin),
        'concerns': ' '.join(f'concern_{x}' for x in concerns),
        'formula': ' '.join(f'{key}_{bool(product[key])}' for key in ['has_fragrance', 'has_alcohol']
                            if product.get(key) is not None),
    }


class ContentIndex:
    FIELD_NAMES = tuple(FIELDS)

    def __init__(self, products, field_weights=None):
        self.field_weights = dict(FIELDS if field_weights is None else field_weights)
        self.products = {}
        for p in products:
            raw = str(p.get('id', ''))
            if p.get('target_type', 'PRODUCT') != 'PRODUCT' or not raw.startswith('mysql_'):
                continue
            try:
                pid = int(raw.removeprefix('mysql_'))
            except ValueError:
                continue
            if pid > 0:
                self.products[pid] = p
        counts = {pid: {field: Counter(text.split() if field in ('skin', 'concerns', 'formula') else tokens(text))
                        for field, text in features(p).items()}
                  for pid, p in self.products.items()}
        frequency = Counter((field, term) for fields in counts.values()
                            for field, words in fields.items() for term in words)
        n = len(counts)
        self.idf = {key: math.log((1 + n) / (1 + df)) + 1 for key, df in frequency.items()}
        self.vectors = {}
        for pid, fields in counts.items():
            vector = {}
            for field, words in fields.items():
                if self.field_weights[field] <= 0:
                    continue
                strengths = self.products[pid].get('skin_scores' if field == 'skin' else 'concern_scores', {}) if field in ('skin','concerns') else {}
                block = normalize({(field, term): (1 + math.log(tf)) * self.idf[(field, term)]
                                   * strengths.get(term.split('_',1)[-1], 1.0)
                                   for term, tf in words.items()})
                strength = max(strengths.values(), default=1.0)
                vector.update({key: value * math.sqrt(self.field_weights[field]) * strength for key, value in block.items()})
            self.vectors[pid] = normalize(vector)
        # Inverted postings score only documents sharing at least one feature.
        self.postings = defaultdict(list)
        for pid, vector in self.vectors.items():
            for feature, value in vector.items():
                self.postings[feature].append((pid, value))
        signature = json.dumps([(pid, features(p), p.get('skin_scores'), p.get('concern_scores'), p.get('stock'), p.get('price'))
                                for pid, p in sorted(self.products.items())], ensure_ascii=True, sort_keys=True)
        self.version = hashlib.sha256((signature + json.dumps(self.field_weights,sort_keys=True)).encode()).hexdigest()[:16]

    def profile(self, interactions, weights, half_life_days, cap, now=None):
        now = now or datetime.now(timezone.utc)
        grouped = defaultdict(float)
        seen = set()
        for row in interactions:
            pid = int(row['product_id'])
            seen.add(pid)
            action = str(row.get('action_type', '')).upper()
            action = {'BUY': 'PURCHASE', 'CART': 'ADD_TO_CART'}.get(action, action)
            weight = weights.get(action, 0)
            created = row.get('created_at')
            if created and half_life_days > 0:
                if isinstance(created, str):
                    created = datetime.fromisoformat(created.replace('Z', '+00:00'))
                if created.tzinfo is None:
                    created = created.replace(tzinfo=timezone.utc)
                age = max(0, (now - created).total_seconds() / 86400)
                weight *= math.exp(-math.log(2) * age / half_life_days)
            if math.isfinite(weight) and weight > 0:
                grouped[pid] += weight
        vector = defaultdict(float)
        for pid, weight in grouped.items():
            for term, value in self.vectors.get(pid, {}).items():
                vector[term] += min(weight, cap) * value
        return normalize(vector), seen, len([p for p in grouped if self.vectors.get(p)])

    def rank(self, query, limit, excluded=(), minimum=0, in_stock_only=True):
        query = normalize(query)
        if not query:
            return []
        excluded = set(excluded)
        scores = defaultdict(float)
        contributions = defaultdict(lambda: defaultdict(float))
        for key, value in query.items():
            for pid, candidate in self.postings.get(key, []):
                if pid in excluded:
                    continue
                scores[pid] += value * candidate
                contributions[pid][key[0]] += value * candidate
        results = []
        for pid, score in sorted(scores.items(), key=lambda item: (-item[1], item[0])):
            product = self.products[pid]
            score = min(1.0, max(0.0, score))
            if score <= 1e-12 or score + 1e-12 < minimum or (in_stock_only and product.get('stock', 0) <= 0):
                continue
            fields = sorted(contributions[pid], key=lambda field: (-contributions[pid][field], field))[:3]
            results.append({'id': pid, 'name': product['name'], 'score': round(min(1.0, score), 8),
                            'source': 'CONTENT_BASED', 'reason': 'Tương đồng về ' + ', '.join(LABELS[f] for f in fields),
                            'field_scores': {field: round(value, 8) for field, value in contributions[pid].items()}})
            if len(results) >= limit:
                break
        return results

    def popular(self, limit, excluded=(), in_stock_only=True):
        excluded = set(excluded)
        rows = sorted(self.products.items(), key=lambda item: (
            -int(bool(item[1].get('is_featured'))), -int(item[1].get('total_sold') or 0),
            -float(item[1].get('rating') or 0), item[0]))
        return [{'id': pid, 'name': p['name'], 'score': None, 'source': 'POPULAR',
                 'reason': 'Sản phẩm nổi bật hoặc bán chạy; chưa có đủ tín hiệu nội dung.'}
                for pid, p in rows if pid not in excluded and (not in_stock_only or p.get('stock', 0) > 0)][:limit]
