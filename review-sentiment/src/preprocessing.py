"""Stateless Vietnamese review preprocessing shared by training and inference."""

from html import unescape
from html.parser import HTMLParser
from functools import lru_cache
import re
import unicodedata

from sklearn.base import BaseEstimator, TransformerMixin

CONFIG = {
    "unicode": "NFC",
    "url_replacement": "urltoken",
    "teencode": {"ko": "không", "khum": "không", "hok": "không", "đc": "được"},
    "preserve": ["punctuation", "emoji", "negation", "intensifiers", "contrast"],
}
URL = re.compile(r"(?:https?://|www\.)[^\s<>]+", re.IGNORECASE)
SHORT = re.compile(r"\b(?:" + "|".join(CONFIG["teencode"]) + r")\b", re.IGNORECASE)
INVISIBLE = re.compile(r"[\x00-\x08\x0e-\x1f\x7f\u200b\ufeff]")


class TextHTML(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.parts = []

    def handle_data(self, data):
        self.parts.append(data)

    def handle_starttag(self, tag, attrs):
        self.parts.append(" ")

    def handle_endtag(self, tag):
        self.parts.append(" ")


def clean(text):
    text = unicodedata.normalize("NFC", unescape(text))
    if re.search(r"</?[A-Za-z][\w:-]*(?:\s[^<>]*)?/?>|<!--", text):
        parser = TextHTML()
        parser.feed(text)
        parser.close()
        text = "".join(parser.parts)
    text = URL.sub(lambda m: " urltoken" + m[0][len(m[0].rstrip(".,!?;:)")):], text)
    text = INVISIBLE.sub("", text)
    text = SHORT.sub(lambda m: CONFIG["teencode"][m[0].lower()], text)
    return " ".join(text.split())


class ReviewCleaner(BaseEstimator, TransformerMixin):
    def fit(self, X, y=None):
        return self

    def transform(self, X):
        return [clean(text) if isinstance(text, str) else self._invalid() for text in X]

    @staticmethod
    def _invalid():
        raise ValueError("Every review must be a string")


@lru_cache(maxsize=16000)
def segment(text):
    from underthesea import word_tokenize

    return word_tokenize(text, format="text")


def word_tokens(text):
    return re.findall(r"\w+|[^\w\s]", text)
