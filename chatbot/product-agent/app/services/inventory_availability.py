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
    variant_ids = set()
    for candidate in candidates:
        product = candidate.product
        if product.targetType == "SERVICE":
            continue
        raw = str(product.id).removeprefix("mysql_")
        if raw.isdigit() and int(raw) > 0:
            product_ids[product.id] = int(raw)
            details = getattr(product, "catalog_details", None)
            if details and details.variants:
                variant_ids.add(int(raw))
    legacy_ids = set(product_ids.values()) - variant_ids
    prices = extractor.getAvailableProductPrices(legacy_ids) if legacy_ids else {}
    live_variants = extractor.getCatalogVariants(variant_ids) if variant_ids else {}
    result = []
    for candidate in candidates:
        product = candidate.product
        product_id = product_ids.get(product.id)
        if product.targetType == "SERVICE":
            result.append(candidate)
        elif product_id in variant_ids:
            from app.schemas.product import CatalogVariant

            indexed = {variant.id: variant for variant in product.catalog_details.variants}
            variants = []
            for row in live_variants.get(product_id, []):
                saved = indexed.get(row["id"])
                variant = CatalogVariant.model_validate(row)
                if saved:
                    variant = saved.model_copy(update={
                        "price": variant.price, "original_price": variant.original_price,
                        "currency": variant.currency, "in_stock": variant.in_stock,
                    })
                variants.append(variant)
            available_prices = [variant.price for variant in variants if variant.in_stock]
            if available_prices:
                details = product.catalog_details.model_copy(update={"variants": variants})
                current = product.model_copy(update={
                    "price": min(available_prices), "inStock": True, "catalog_details": details,
                })
                result.append(candidate.model_copy(update={"product": current}))
        elif product_id in prices:
            current = product.model_copy(update={"price": prices[product_id], "inStock": True})
            result.append(candidate.model_copy(update={"product": current}))
    return result
