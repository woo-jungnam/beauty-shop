"""Validated RecSys controls stored in the existing admin system_configs table."""
import math
from time import monotonic
from threading import RLock

PREFIX = 'recommendation.'
SPECS = {
    'weight.view': (1.0, 0, 20), 'weight.search_click': (1.5, 0, 20),
    'weight.add_to_cart': (3.0, 0, 20), 'weight.purchase': (5.0, 0, 20),
    'history_window': (100, 1, 1000), 'half_life_days': (30.0, 0, 3650),
    'max_product_weight': (10.0, 0.1, 100), 'min_similarity': (0.0, 0, 1),
    'catalog_refresh_seconds': (30, 5, 3600), 'exclude_seen': (True, None, None),
    'in_stock_only': (True, None, None),
    'field.name': (1.0, 0, 10), 'field.category': (2.0, 0, 10), 'field.brand': (0.5, 0, 10),
    'field.description': (1.0, 0, 10), 'field.ingredients': (3.0, 0, 10),
    'field.skin': (2.0, 0, 10), 'field.concerns': (2.0, 0, 10), 'field.formula': (0.5, 0, 10),
}


def validate(values):
    result = {key: spec[0] for key, spec in SPECS.items()}
    for key, raw in values.items():
        if key not in SPECS:
            raise ValueError('Khóa cấu hình gợi ý không được hỗ trợ: ' + key)
        default, low, high = SPECS[key]
        if isinstance(default, bool):
            if str(raw).lower() not in ('true', 'false'):
                raise ValueError('Giá trị bật/tắt chưa hợp lệ: ' + key)
            value = str(raw).lower() == 'true'
        else:
            if isinstance(raw, bool):
                raise ValueError('Giá trị số chưa hợp lệ: ' + key)
            value = float(raw)
            if not math.isfinite(value) or not low <= value <= high:
                raise ValueError('Giá trị nằm ngoài phạm vi: ' + key)
            if isinstance(default, int):
                if not value.is_integer():
                    raise ValueError('Giá trị phải là số nguyên: ' + key)
                value = int(value)
        result[key] = value
    if not any(result['weight.' + key] > 0 for key in ('view', 'search_click', 'add_to_cart', 'purchase')):
        raise ValueError('Cần ít nhất một trọng số hành vi lớn hơn 0.')
    if not any(value > 0 for key,value in result.items() if key.startswith('field.')):
        raise ValueError('Cần ít nhất một nhóm thuộc tính có trọng số lớn hơn 0.')
    return result


class RecommendationConfig:
    def __init__(self, extractor):
        self.extractor = extractor
        self.values = validate({})
        self.expires = 0
        self.error = None
        self.lock = RLock()

    def get(self, force=False):
        with self.lock:
            if not force and monotonic() < self.expires:
                return dict(self.values)
            conn = None
            try:
                conn = self.extractor.getConnection()
                with conn.cursor() as cursor:
                    cursor.execute("SELECT config_key, config_value FROM system_configs WHERE is_deleted=0 AND config_key LIKE 'recommendation.%'")
                    rows = cursor.fetchall()
                self.values = validate({r['config_key'].removeprefix(PREFIX): r['config_value'] for r in rows})
                self.error = None
            except Exception:
                self.error = 'Chưa tải được cấu hình mới; đang giữ cấu hình hợp lệ gần nhất.'
            finally:
                if conn:
                    conn.close()
            self.expires = monotonic() + 5
            return dict(self.values)
