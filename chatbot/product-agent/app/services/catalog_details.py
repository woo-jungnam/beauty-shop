from app.schemas.product import CatalogDetails, CatalogVariant
from app.services.ingestion.cleaning import TextCleaner


def format_variant(variant: CatalogVariant, include_availability: bool = True) -> str:
    facts = [variant.name]
    if variant.volume:
        facts.append(f"Dung tích: {variant.volume}")
    if variant.color:
        facts.append(f"Màu: {variant.color}")
    if variant.sku:
        facts.append(f"SKU: {variant.sku}")
    if include_availability:
        facts.append(f"Giá: {variant.price:,.0f} {variant.currency}")
        if variant.original_price is not None and variant.original_price > variant.price:
            facts.append(f"Giá gốc: {variant.original_price:,.0f} {variant.currency}")
        facts.append("Còn hàng" if variant.in_stock else "Hết hàng")
    facts.extend(variant.attributes)
    return " | ".join(TextCleaner.clean(fact) for fact in facts)


def catalog_evidence(details: CatalogDetails, include_availability: bool = True) -> str:
    facts = [TextCleaner.clean(spec) for spec in details.specifications]
    facts.extend(format_variant(variant, include_availability) for variant in details.variants)
    return "; ".join(facts)
