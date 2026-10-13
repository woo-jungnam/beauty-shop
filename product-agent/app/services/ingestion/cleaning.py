import re
import unicodedata
from typing import Optional

COSMETIC_SYNONYMS = {
    r"\bkcn\b": "kem chống nắng",
    r"\bsrm\b": "sữa rửa mặt",
    r"\btt\b": "tẩy trang",
    r"\bkl\b": "khối lượng",
    r"\bdt\b": "dung tích",
    r"\bko\b": "không",
    r"\bk\b": "không",
    r"\bdc\b": "được",
    r"\boki\b": "ok",
    r"\bthâm đỏ\b": "thâm đỏ pie",
    r"\bthâm đen\b": "thâm nâu pih",
    r"\bthâm nâu\b": "thâm nâu pih",
    r"\bda dầu mụn\b": "da dầu mụn nhờn",
    r"\bkiềm dầu\b": "kiểm soát bã nhờn kiềm dầu",
    r"\bphục hồi\b": "phục hồi tái tạo hàng rào da",
    r"\bb5\b": "vitamin b5 panthenol",
    r"\bha\b": "hyaluronic acid ha cấp ẩm",
    r"\bvit c\b": "vitamin c dưỡng sáng mờ thâm",
    r"\bvitamin c\b": "vitamin c dưỡng sáng mờ thâm",
    r"\bbha\b": "salicylic acid bha",
    r"\baha\b": "glycolic acid lactic acid aha",
    r"\bniac\b": "niacinamide",
    r"\bngừa mụn\b": "kháng viêm ngừa mụn",
}

PRICE_PATTERN = re.compile(
    r"(\d+(?:[.,]\d+)?)\s*(k|nghìn|ngàn|triệu|củ|tr|vnd|đ)?",
    re.IGNORECASE,
)

EMOJI_PATTERN = re.compile(
    r"["
    r"\U0001F000-\U0001FAFF"  # Emoticons, symbols, pictographs, flags
    r"\u2600-\u27BF"          # Misc symbols & dingbats
    r"\u2300-\u23FF"          # Misc technical (stopwatch, etc.)
    r"\u2B50-\u2B55"          # Medium stars, circles
    r"\u200D"                 # Zero width joiner
    r"\uFE0E\uFE0F"           # Variation selectors
    r"]+",
    flags=re.UNICODE,
)


def remove_emojis(text: str) -> str:
    if not text:
        return ""
    cleaned = EMOJI_PATTERN.sub("", str(text))
    return re.sub(r" {2,}", " ", cleaned)


removeEmojis = remove_emojis


class TextCleaner:

    @staticmethod
    def remove_emojis(text: str) -> str:
        return remove_emojis(text)

    removeEmojis = remove_emojis

    @staticmethod
    def clean(text: str) -> str:
        if not text:
            return ""
        text = remove_emojis(text)
        text = unicodedata.normalize("NFKC", text)
        text = re.sub(r"<[^>]+>", " ", text)
        text = re.sub(r"[\r\n\t]+", " ", text)
        return re.sub(r"\s{2,}", " ", text).strip()

    @staticmethod
    def normalize(text: str, expandSynonyms: bool = False, **kwargs) -> str:
        expandSynonyms = kwargs.get("expand_synonyms", expandSynonyms)
        cleaned = TextCleaner.clean(text)
        if not cleaned:
            return ""
        lowered = cleaned.lower()
        if expandSynonyms:
            for pattern, replacement in COSMETIC_SYNONYMS.items():
                lowered = re.sub(pattern, replacement, lowered, flags=re.IGNORECASE)
        return re.sub(r"\s{2,}", " ", lowered).strip()

    @staticmethod
    def parsePrice(priceStr: str, **kwargs) -> Optional[int]:
        priceStr = kwargs.get("price_str", priceStr)
        if not priceStr:
            return None
        s = str(priceStr).lower().strip()

        mComposite = re.search(r"(\d+)\s*(?:tr|triệu|củ)\s*(\d+)", s)
        if mComposite:
            major = int(mComposite.group(1))
            minor = int(mComposite.group(2))
            return int(major * 1_000_000 + minor * 100_000)

        mThousands = re.search(r"(\d{1,3}(?:[.,]\d{3})+)\s*(?:đ|vnd)?", s)
        if mThousands:
            cleanNum = re.sub(r"[.,]", "", mThousands.group(1))
            return int(cleanNum)

        match = PRICE_PATTERN.search(s)
        if not match:
            return None
        numberStr = match.group(1).replace(",", ".")
        try:
            val = float(numberStr)
        except ValueError:
            return None
        unit = (match.group(2) or "").lower()
        if unit in ["k", "nghìn", "ngàn"]:
            return int(val * 1000)
        elif unit in ["triệu", "tr", "củ"]:
            return int(val * 1_000_000)
        elif val < 1000:
            return int(val * 1000)
        return int(val)

    parse_price = parsePrice


clean_text = TextCleaner.clean
cleanText = TextCleaner.clean
normalize_cosmetics_text = TextCleaner.normalize
normalizeCosmeticsText = TextCleaner.normalize
parse_price_to_vnd = TextCleaner.parsePrice
parsePriceToVnd = TextCleaner.parsePrice

__all__ = [
    "COSMETIC_SYNONYMS",
    "PRICE_PATTERN",
    "TextCleaner",
    "cleanText",
    "clean_text",
    "normalizeCosmeticsText",
    "normalize_cosmetics_text",
    "parsePriceToVnd",
    "parse_price_to_vnd",
    "removeEmojis",
    "remove_emojis",
]
