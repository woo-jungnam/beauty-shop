import re
import unicodedata


def _words(value: str) -> str:
    text = unicodedata.normalize("NFKD", value.casefold())
    text = "".join(char for char in text if not unicodedata.combining(char))
    text = re.sub(r"['’‘ʼ]", "", text)
    text = re.sub(r"\bl\s+oreal\b", "loreal", text)
    return " ".join(re.findall(r"[^\W_]+", text))


# Explicit labels only. Ambiguous packaging leaves type filtering disabled.
PRODUCT_FAMILIES = {
    "micellar_water": ("micellar water", "nuoc tay trang"),
    "shampoo": ("shampoo", "dau goi"),
    "body_wash": ("body wash", "shower gel", "sua tam"),
    "sunscreen": ("sunscreen", "sun cream", "kem chong nang"),
    "moisturizer": ("moisturizer", "moisturiser", "kem duong am"),
}


def _brand_key(value: str) -> str:
    name = _words(value)
    return "loreal" if name == "loreal paris" else name


def printed_brand(brand: str | None, packaging_text: str | None) -> str | None:
    if not isinstance(brand, str) or not isinstance(packaging_text, str):
        return None
    key = _brand_key(brand)
    if len(key.replace(" ", "")) < 3:
        return None
    return key if f" {key} " in f" {_words(packaging_text)} " else None


def matches_printed_brand(candidate: dict, key: str) -> bool:
    brand = candidate.get("brand_name")
    if isinstance(brand, str) and brand.strip():
        return _brand_key(brand) == key
    # Older index points can lack structured brand metadata.
    name = candidate.get("product_name") or ""
    return f" {key} " in f" {_words(name)} "


def _families(value: str) -> set[str]:
    text = f" {_words(value)} "
    return {
        family for family, labels in PRODUCT_FAMILIES.items()
        if any(f" {label} " in text for label in labels)
    }


def printed_product_family(packaging_text: str | None) -> str | None:
    families = _families(packaging_text) if isinstance(packaging_text, str) else set()
    return next(iter(families)) if len(families) == 1 else None


def matches_printed_family(candidate: dict, family: str) -> bool:
    families = _families(candidate.get("product_name") or "")
    # Keep older or abbreviated names without explicit type metadata.
    return not families or family in families
