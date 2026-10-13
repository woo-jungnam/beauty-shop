import time
from datetime import datetime, timezone
from threading import RLock

from app.core.logging import getLogger
from app.services.ingestion.extractor import DatabaseExtractor
from app.services.content_based import ContentIndex
from app.services.recommendation_config import RecommendationConfig

logger = getLogger('recommendationService')
ACTION_WEIGHTS = {'VIEW': 1.0, 'SEARCH_CLICK': 1.5, 'ADD_TO_CART': 3.0, 'PURCHASE': 5.0}


class RecommendationService:
    def __init__(self, extractor=None):
        self.dbExtractor = extractor or DatabaseExtractor()
        self.config = RecommendationConfig(self.dbExtractor)
        self.index = None
        self.expires = 0
        self.built_at = None
        self.build_ms = None
        self.error = None
        self.lock = RLock()
        self.requests = {'CONTENT_BASED': 0, 'COLD_START': 0, 'MIXED': 0, 'UNAVAILABLE': 0}

    def refresh(self, force=False):
        with self.lock:
            config = self.config.get(force=force)
            fields = {name:config['field.' + name] for name in ContentIndex.FIELD_NAMES}
            if not force and self.index is not None and time.monotonic() < self.expires and self.index.field_weights == fields:
                return self.index, config
            watch = time.perf_counter()
            try:
                index = ContentIndex(self.dbExtractor.extractProducts(), fields)
                self.index = index
                self.built_at = datetime.now(timezone.utc).isoformat()
                self.build_ms = round((time.perf_counter() - watch) * 1000, 2)
                self.error = None
                self.expires = time.monotonic() + config['catalog_refresh_seconds']
                return index, config
            except Exception:
                logger.exception('Không thể làm mới dữ liệu Content-Based')
                self.error = 'Chưa làm mới được dữ liệu sản phẩm. Vui lòng kiểm tra kết nối cơ sở dữ liệu.'
                # Do not use expired candidate eligibility when the catalog cannot be verified.
                return None, config

    def status(self):
        index, config = self.refresh()
        return {'status': 'ok' if index is not None and not self.config.error else 'degraded',
                'algorithm': 'FIELD_TFIDF_WEIGHTED_CENTROID_COSINE', 'embedding_space': 'TFIDF_ADMIN_CATALOG_V1',
                'index_version': index.version if index else None,
                'indexed_products': len(index.products) if index else 0,
                'features': len(index.idf) if index else 0, 'last_refresh': self.built_at,
                'build_ms': self.build_ms, 'config': config, 'config_error': self.config.error,
                'error': self.error, 'request_counts': dict(self.requests)}

    def _result(self, items, source, index, matched_products=0):
        with self.lock:
            self.requests[source] = self.requests.get(source, 0) + 1
        return {'status': 'success' if index is not None else 'unavailable',
                'algorithm': 'FIELD_TFIDF_WEIGHTED_CENTROID_COSINE' if source in ('CONTENT_BASED', 'MIXED') else 'POPULAR_FALLBACK',
                'source': source, 'recommended_product_ids': [r['id'] for r in items],
                'items': items, 'total': len(items), 'matched_history_products': matched_products,
                'index_version': index.version if index else None, 'detail': self.error}

    def similar(self, product_id, limit=8):
        index, config = self.refresh()
        if index is None:
            return self._result([], 'UNAVAILABLE', None)
        vector = index.vectors.get(product_id)
        items = index.rank(vector, limit, {product_id}, config['min_similarity'], config['in_stock_only'])
        result = self._result(items, 'CONTENT_BASED', index)
        result['product_id'] = product_id
        return result

    def history(self, user_id, session_id, window):
        conn = self.dbExtractor.getConnection()
        try:
            with conn.cursor() as cur:
                if user_id:
                    where = 'user_id=%s'
                    params = [user_id]
                    if session_id:
                        where += ' OR (user_id IS NULL AND session_id=%s)'
                        params.append(session_id)
                else:
                    where = 'user_id IS NULL AND session_id=%s'
                    params = [session_id]
                cur.execute('SELECT product_id,action_type,created_at FROM user_product_interactions WHERE '
                            + where + ' ORDER BY created_at DESC,id DESC LIMIT %s', tuple(params + [window]))
                return cur.fetchall()
        finally:
            conn.close()

    def for_user(self, user_id=None, session_id=None, limit=8):
        index, config = self.refresh()
        if index is None:
            return self._result([], 'UNAVAILABLE', None)
        history = []
        if user_id or session_id:
            try:
                history = self.history(user_id, session_id, config['history_window'])
            except Exception:
                logger.exception('Chưa đọc được lịch sử tương tác')
                result = self._result(index.popular(limit, in_stock_only=config['in_stock_only']), 'COLD_START', index)
                result['history_error'] = 'Chưa xác minh được lịch sử; đang dùng sản phẩm phổ biến.'
                return result
        weights = {'VIEW': config['weight.view'], 'SEARCH_CLICK': config['weight.search_click'],
                   'ADD_TO_CART': config['weight.add_to_cart'], 'PURCHASE': config['weight.purchase']}
        profile, seen, matched = index.profile(history, weights, config['half_life_days'], config['max_product_weight'])
        excluded = seen if config['exclude_seen'] else set()
        items = index.rank(profile, limit, excluded, config['min_similarity'], config['in_stock_only'])
        source = 'CONTENT_BASED' if items else 'COLD_START'
        if len(items) < limit:
            popular = index.popular(limit - len(items), excluded | {r['id'] for r in items}, config['in_stock_only'])
            if items and popular:
                source = 'MIXED'
            items += popular
        return self._result(items, source, index, matched)

    # Compatibility for scripts/consumers that need only IDs.
    def getSimilarProducts(self, productId, limit=8):
        return self.similar(productId, limit)['recommended_product_ids']

    def getRecommendationsForUser(self, userId=None, sessionId=None, limit=8):
        return self.for_user(userId, sessionId, limit)['recommended_product_ids']


recommendationService = RecommendationService()
