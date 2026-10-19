from datetime import datetime, timedelta, timezone


VIETNAM = timezone(timedelta(hours=7))
MIN_REMAINING_DAYS = 30


def sale_cutoff(now=None):
    return (now or datetime.now(timezone.utc)).astimezone(VIETNAM).date() + timedelta(days=MIN_REMAINING_DAYS)


AVAILABLE_STOCK_SQL = """
    SELECT s.product_variant_id, SUM(s.quantity - s.reserved_quantity) AS available
    FROM warehouse_stocks s
    JOIN warehouses w ON w.id = s.warehouse_id
    WHERE s.is_deleted = 0 AND s.is_blocked = 0
      AND w.is_active = 1 AND w.is_deleted = 0
      AND (s.expiration_date IS NULL OR s.expiration_date > %s)
      AND s.quantity - s.reserved_quantity > 0
    GROUP BY s.product_variant_id
"""


def filter_available_candidates(candidates, extractor):
    product_ids = {}
    for candidate in candidates:
        product = candidate.product
        if product.targetType == "SERVICE":
            continue
        raw = str(product.id).removeprefix("mysql_")
        if raw.isdigit() and int(raw) > 0:
            product_ids[product.id] = int(raw)
    prices = extractor.getAvailableProductPrices(product_ids.values()) if product_ids else {}
    result = []
    for candidate in candidates:
        product = candidate.product
        if product.targetType == "SERVICE":
            result.append(candidate)
        elif product_ids.get(product.id) in prices:
            current = product.model_copy(update={"price": prices[product_ids[product.id]], "inStock": True})
            result.append(candidate.model_copy(update={"product": current}))
    return result
