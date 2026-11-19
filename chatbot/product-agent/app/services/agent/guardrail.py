import re
from typing import Any, List, Optional, Tuple
from app.core.logging import getLogger
from app.services.ingestion.cleaning import remove_emojis

logger = getLogger("generation_guardrail")


class GenerationGuardrail:

    @classmethod
    def verifyResponse(
        cls,
        botResponse: str,
        candidates: List[Any],
        **kwargs,
    ) -> Tuple[bool, str, List[str]]:
        botResponse = kwargs.get("bot_response", botResponse)
        if not botResponse or not botResponse.strip():
            return False, "Rất tiếc, đã có lỗi khi sinh câu trả lời tư vấn.", ["Empty response"]

        botResponse = remove_emojis(botResponse)

        if not candidates:
            return True, botResponse, []

        issues: List[str] = []
        products = [getattr(candidate, "product", candidate) for candidate in candidates]
        citations = [int(value) for value in re.findall(r"\[(\d+)\]", botResponse)]
        if not citations or any(index < 1 or index > len(products) for index in citations):
            issues.append("Missing or invalid product citations")

        if products and not any(
            product.name and product.name.strip().casefold() in botResponse.casefold()
            for product in products
        ):
            issues.append("No catalog product name found")

        # Prices must match the one product cited on the same line. Use the
        # catalog template if an amount cannot be attributed unambiguously.
        for line in botResponse.splitlines():
            amounts = re.findall(
                r"(?<![\w.,])(?:\d{1,3}(?:[., ]\d{3})+|\d+)\s*(?:vnd|đồng|đ|₫|nghìn|ngàn|k|triệu)(?!\w)"
                r"|(?<![\w.,])(?:\d{1,3}(?:[.,]\d{3})+|\d{5,})(?![\w.,])",
                line, re.IGNORECASE,
            )
            if not amounts:
                continue
            line_citations = {int(value) for value in re.findall(r"\[(\d+)\]", line)}
            if len(line_citations) != 1 or not line_citations.issubset(range(1, len(products) + 1)):
                issues.append("Price has no unique product citation")
                continue
            product = products[next(iter(line_citations)) - 1]
            for value in amounts:
                number = re.search(r"\d[\d., ]*", value).group().strip()
                unit = value[len(number):].strip().lower()
                if unit in ("k", "nghìn", "ngàn", "triệu"):
                    if not number.isdecimal():
                        issues.append("Ambiguous abbreviated price")
                        continue
                    price = int(number) * (1_000_000 if unit == "triệu" else 1_000)
                else:
                    price = int(re.sub(r"[^0-9]", "", number))
                if price != round(product.price):
                    issues.append("Price does not match cited product")

        return not issues, botResponse, issues

    verify_response = verifyResponse


generationGuardrail = GenerationGuardrail()
generation_guardrail = generationGuardrail
