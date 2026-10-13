from enum import Enum
from typing import Dict, List, Optional, Set


class ProductDomain(str, Enum):
    SKINCARE = "SKINCARE"
    HAIRCARE = "HAIRCARE"
    BODYCARE = "BODYCARE"
    LIPCARE = "LIPCARE"
    MAKEUP = "MAKEUP"
    FRAGRANCE = "FRAGRANCE"


DB_CATEGORY_TO_DOMAIN: Dict[str, ProductDomain] = {
    "cleanser": ProductDomain.SKINCARE,
    "sunscreen": ProductDomain.SKINCARE,
    "toner": ProductDomain.SKINCARE,
    "serum": ProductDomain.SKINCARE,
    "cream": ProductDomain.SKINCARE,
    "Mặt nạ": ProductDomain.SKINCARE,
    "Tẩy tế bào chết": ProductDomain.SKINCARE,
    "Xịt khoáng": ProductDomain.SKINCARE,

    "Dầu gội": ProductDomain.HAIRCARE,
    "Dầu xả": ProductDomain.HAIRCARE,

    "Sữa tắm": ProductDomain.BODYCARE,
    "Khử mùi": ProductDomain.BODYCARE,
    "Kem dưỡng thể": ProductDomain.BODYCARE,

    "Son môi": ProductDomain.LIPCARE,
    "Dưỡng môi": ProductDomain.LIPCARE,

    "Kem nền": ProductDomain.MAKEUP,
    "Phấn phủ": ProductDomain.MAKEUP,
    "Má hồng": ProductDomain.MAKEUP,
    "Kẻ mắt": ProductDomain.MAKEUP,
    "Mascara": ProductDomain.MAKEUP,
    "Chì kẻ mày": ProductDomain.MAKEUP,
    "Mút trang điểm": ProductDomain.MAKEUP,
    "Bông tẩy trang": ProductDomain.SKINCARE,

    "Nước hoa": ProductDomain.FRAGRANCE,
}

CATEGORY_SYNONYMS: Dict[str, List[str]] = {
    "serum": ["serum", "tinh chất", "ampoule", "essence", "huyết thanh"],
    "sunscreen": ["kem chống nắng", "kcn", "sunscreen", "sunblock", "sữa chống nắng", "xịt chống nắng"],
    "cleanser": ["sữa rửa mặt", "srm", "gel rửa mặt", "nước tẩy trang", "tẩy trang", "cleanser", "dầu tẩy trang", "sáp tẩy trang", "micellar water"],
    "toner": ["toner", "tonner", "nước hoa hồng", "nước cân bằng", "pad dưỡng da"],
    "cream": ["kem dưỡng", "kem ẩm", "moisturizer", "cream", "kem dưỡng ẩm", "kem phục hồi"],
    "Kem dưỡng thể": ["kem dưỡng thể", "sữa dưỡng thể", "body lotion", "dưỡng thể", "body cream", "lotion dưỡng thể"],
    "Mặt nạ": ["mặt nạ", "mask", "sheet mask", "mặt nạ đất sét", "mặt nạ ngủ", "sleeping mask"],
    "Tẩy tế bào chết": ["tẩy tế bào chết", "tẩy da chết", "exfoliant", "scrub", "peeling", "bha liquid", "aha liquid"],
    "Xịt khoáng": ["xịt khoáng", "face mist", "mist", "nước xịt khoáng"],
    "Bông tẩy trang": ["bông tẩy trang", "cotton pad"],

    "Dầu gội": ["dầu gội", "shampoo", "dầu gội đầu", "gội đầu"],
    "Dầu xả": ["dầu xả", "conditioner", "kem xả", "ủ tóc", "hair mask"],

    "Sữa tắm": ["sữa tắm", "shower gel", "body wash", "xà phòng tắm", "gel tắm"],
    "Khử mùi": ["khử mùi", "lăn khử mùi", "xịt khử mùi", "deodorant", "lăn nách"],

    "Son môi": ["son môi", "son", "lipstick", "son thỏi", "son kem", "son bóng", "son tint", "son lì"],
    "Dưỡng môi": ["dưỡng môi", "son dưỡng", "sáp dưỡng môi", "lip balm", "lip mask", "mặt nạ môi"],

    "Kem nền": ["kem nền", "foundation", "cushion", "bb cream", "cc cream", "phấn nước"],
    "Phấn phủ": ["phấn phủ", "powder", "phấn nền", "setting powder", "phấn kiềm dầu", "loose powder"],
    "Má hồng": ["má hồng", "blush", "phấn má"],
    "Kẻ mắt": ["kẻ mắt", "eyeliner", "bút kẻ mắt"],
    "Mascara": ["mascara", "chuốt mi"],
    "Chì kẻ mày": ["chì kẻ mày", "kẻ mày", "eyebrow", "bút kẻ mày"],
    "Mút trang điểm": ["mút trang điểm", "bông mút", "sponge", "cọ trang điểm", "beauty blender"],

    "Nước hoa": ["nước hoa", "perfume", "fragrance", "dầu thơm", "eau de parfum", "edp", "edt"],
}

DOMAIN_KEYWORDS: Dict[ProductDomain, List[str]] = {
    ProductDomain.HAIRCARE: [
        "tóc", "da đầu", "gàu", "rụng tóc", "bết tóc", "xơ rối", "chẻ ngọn",
        "hói", "mượt tóc", "gội", "xả tóc", "tóc dầu",
    ],
    ProductDomain.LIPCARE: [
        "môi", "khô môi", "thâm môi", "nứt nẻ môi", "bong tróc môi", "dưỡng môi", "màu son",
    ],
    ProductDomain.BODYCARE: [
        "cơ thể", "body", "toàn thân", "viêm nang lông", "mùi cơ thể", "hôi nách",
        "nách", "tắm", "dưỡng thể",
    ],
    ProductDomain.MAKEUP: [
        "trang điểm", "makeup", "che khuyết điểm", "tone da", "nâng tone",
        "lớp nền", "đánh phấn", "chuốt mi", "lông mày",
    ],
    ProductDomain.FRAGRANCE: [
        "nước hoa", "mùi hương", "thơm lâu", "hương thơm", "lưu hương", "mùi gỗ", "mùi hoa",
    ],
    ProductDomain.SKINCARE: [
        "da mặt", "da dầu", "da khô", "da mụn", "mụn", "thâm", "nám", "tàn nhang",
        "lỗ chân lông", "lão hóa", "nếp nhăn", "tàn nhang", "phục hồi da",
        "da nhạy cảm", "b5", "niacinamide", "retinol", "salicylic acid", "bha", "aha",
    ],
}

SKIN_TYPE_APPLICABLE_CATEGORIES: Set[str] = {
    "cleanser",
    "sunscreen",
    "toner",
    "serum",
    "cream",
    "Mặt nạ",
    "Tẩy tế bào chết",
    "Xịt khoáng",
    "Kem nền",
}


class Taxonomy:

    @staticmethod
    def normalizeCategory(categoryInput: Optional[str], **kwargs) -> Optional[str]:
        categoryInput = kwargs.get("category_input", categoryInput)
        if not categoryInput:
            return None

        raw = categoryInput.strip().lower()

        for stdCat in DB_CATEGORY_TO_DOMAIN.keys():
            if raw == stdCat.lower():
                return stdCat

        for stdCat, synList in CATEGORY_SYNONYMS.items():
            if raw == stdCat.lower() or any(syn in raw for syn in synList):
                return stdCat

        return categoryInput.strip()

    @staticmethod
    def getDomain(category: Optional[str]) -> Optional[ProductDomain]:
        if not category:
            return None
        if category in DB_CATEGORY_TO_DOMAIN:
            return DB_CATEGORY_TO_DOMAIN[category]
        cLow = category.strip().lower()
        for cat, domain in DB_CATEGORY_TO_DOMAIN.items():
            if cat.lower() == cLow:
                return domain
        return None

    @staticmethod
    def getProductDomain(
        category: Optional[str],
        name: str = "",
        description: str = "",
    ) -> Optional[ProductDomain]:
        text = f"{name} {description}".lower()

        if any(kw in text for kw in ["dầu gội", "dầu xả", "shampoo", "conditioner", "gội đầu", "xả tóc"]):
            return ProductDomain.HAIRCARE

        if any(kw in text for kw in ["dưỡng thể", "body lotion", "body wash", "body cream", "sữa tắm", "khử mùi", "lăn nách"]):
            return ProductDomain.BODYCARE

        if any(kw in text for kw in ["son môi", "son thỏi", "son kem", "son bóng", "son tint", "dưỡng môi", "son dưỡng"]):
            return ProductDomain.LIPCARE

        if any(kw in text for kw in ["nước hoa", "perfume", "dầu thơm"]):
            return ProductDomain.FRAGRANCE

        return Taxonomy.getDomain(category)

    @staticmethod
    def inferDomainFromQuery(
        queryText: str = "",
        concerns: Optional[List[str]] = None,
        **kwargs,
    ) -> Optional[ProductDomain]:
        queryText = kwargs.get("query_text", queryText) or kwargs.get("queryText", queryText)
        searchCorpus = (queryText or "").lower()
        if concerns:
            searchCorpus += " " + " ".join(c.lower() for c in concerns)

        domainPriority = [
            ProductDomain.HAIRCARE,
            ProductDomain.LIPCARE,
            ProductDomain.BODYCARE,
            ProductDomain.MAKEUP,
            ProductDomain.FRAGRANCE,
            ProductDomain.SKINCARE,
        ]

        for dom in domainPriority:
            keywords = DOMAIN_KEYWORDS.get(dom, [])
            if any(kw in searchCorpus for kw in keywords):
                return dom

        return None

    @staticmethod
    def isSkinTypeApplicable(category: Optional[str]) -> bool:
        if not category:
            return False
        cLow = category.strip().lower()
        return any(cLow == item.lower() for item in SKIN_TYPE_APPLICABLE_CATEGORIES)

    normalize_category = normalizeCategory
    get_domain = getDomain
    get_product_domain = getProductDomain
    infer_domain_from_query = inferDomainFromQuery
    is_skin_type_applicable = isSkinTypeApplicable


taxonomy = Taxonomy()
taxonomyManager = taxonomy
