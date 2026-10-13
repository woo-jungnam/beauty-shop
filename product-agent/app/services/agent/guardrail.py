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
        candidateProds = [getattr(c, "product", c) for c in candidates]
        candidateNames = [getattr(p, "name", "").strip().lower() for p in candidateProds if getattr(p, "name", "")]
        candidateBrands = [getattr(p, "brand", "").strip().lower() for p in candidateProds if getattr(p, "brand", "")]

        respLower = botResponse.lower()

        matchedAnyBrand = any(b in respLower for b in candidateBrands if len(b) > 2)
        matchedAnyName = any(n[:15] in respLower for n in candidateNames if len(n) >= 15) or any(n in respLower for n in candidateNames)

        if not (matchedAnyBrand or matchedAnyName):
            issues.append("Lack of candidate mentions in response")

        foundPrices = re.findall(r"\b(\d{1,3}(?:[.,]\d{3})+)\s*(?:đ|vnd|đồng)?\b", botResponse, re.I)
        numericPrices = []
        for fp in foundPrices:
            cleanPrice = fp.replace(".", "").replace(",", "")
            try:
                numericPrices.append(float(cleanPrice))
            except ValueError:
                pass

        candPrices = [float(getattr(p, "price", 0.0)) for p in candidateProds]
        for numP in numericPrices:
            if numP > 10_000:
                isCloseToAny = any(abs(numP - cp) / max(cp, 1.0) < 0.35 for cp in candPrices)
                if not isCloseToAny:
                    issues.append(f"Price mismatch: {numP:,.0f} VND")

        sanitized = botResponse
        isValid = len(issues) == 0 or (matchedAnyBrand or matchedAnyName)
        return isValid, sanitized, issues

    verify_response = verifyResponse


generationGuardrail = GenerationGuardrail()
generation_guardrail = generationGuardrail
