import re
from difflib import SequenceMatcher
from typing import List, Optional

from app.core.logging import LatencyTracker, getLogger
from app.core.safety import ingredientSafetyEngine
from app.llm.client import LLMClientError, llmClient
from app.llm.prompts import buildDermatologyGenerationPrompt
from app.schemas.query import UnderstandingResult
from app.schemas.retrieval import RerankedCandidate
from app.services.agent.guardrail import generationGuardrail
from app.services.catalog_details import catalog_evidence, format_variant
from app.services.ingestion.cleaning import TextCleaner, remove_emojis

logger = getLogger("generationService")

CATALOG_FIELDS = {
    "usage": ("Cách dùng", r"cách (dùng|sử dụng|thoa|bôi)|dùng (sao|thế nào|như thế nào)|bao lâu|mấy lần"),
    "ingredients": ("Thành phần", r"thành phần|hoạt chất|chứa|có cồn|có hương liệu"),
    "benefits": ("Công dụng", r"công dụng|tác dụng|hiệu quả|trị khỏi|chữa khỏi|cam kết"),
}
VARIANT_QUESTION = re.compile(
    r"\bdung tích\b|bao nhiêu\s*(?:ml|lít|gram|gam)\b|khối lượng|màu (?:nào|gì|sắc)|biến thể|phiên bản|quy cách|\bsku\b",
    re.IGNORECASE,
)
CATALOG_SPEC_QUESTION = re.compile(
    r"xuất xứ|sản xuất ở đâu|nhà sản xuất|bảo quản|hạn sử dụng|đối tượng|thông số|thông tin chi tiết",
    re.IGNORECASE,
)
SIZE_PATTERN = re.compile(r"(?<!\w)(\d+(?:[.,]\d+)?)\s*(ml|lít|l|kg|g|gam|gram)\b", re.IGNORECASE)


def catalog_sizes(text: str) -> set[tuple[str, str]]:
    return {(value.replace(",", "."), {"lít": "l", "gam": "g", "gram": "g"}.get(unit, unit))
            for value, unit in SIZE_PATTERN.findall(text.lower())}


def requested_catalog_fields(message: str) -> list[str]:
    return [field for field, (_, pattern) in CATALOG_FIELDS.items()
            if re.search(pattern, message, re.IGNORECASE)]


class ContextBuilder:

    @staticmethod
    def buildProductContext(
        candidates: List[RerankedCandidate],
        maxProducts: int = 2,
        understanding: Optional[UnderstandingResult] = None,
        **kwargs,
    ) -> str:
        maxProds = maxProducts if "maxProducts" in kwargs or maxProducts != 2 else kwargs.get("max_products", maxProducts)
        und = understanding if understanding is not None else kwargs.get("understanding")
        if not candidates:
            return "Không tìm thấy sản phẩm nào phù hợp với các tiêu chí tìm kiếm."

        blocks: List[str] = []
        for idx, cand in enumerate(candidates[:maxProds], 1):
            p = cand.product
            priceFmt = f"{p.price:,.0f} VND"
            skins = ", ".join(p.skinType) or "Chưa có dữ liệu"
            concerns = ", ".join(p.concerns) or "Chưa có dữ liệu"
            pType = getattr(p, "targetType", "PRODUCT") or getattr(p, "target_type", "PRODUCT") or "PRODUCT"
            duration = getattr(p, "durationMinutes", 0) or getattr(p, "duration_minutes", 0) or 0
            durationStr = f" ({duration}p)" if duration else ""
            if pType == "SERVICE":
                block = f"[{idx}] {p.name}{durationStr} | Cơ sở: {p.brand} | Giá: {priceFmt} | Phù hợp: {skins} ({concerns})"
            else:
                stock = "Còn hàng" if p.inStock else "Hết hàng"
                block = f"[{idx}] {p.name} | Hãng: {p.brand} | Giá: {priceFmt} | Tồn kho: {stock} | Da: {skins} | Vấn đề: {concerns}"
            for field, (label, _) in CATALOG_FIELDS.items():
                evidence = TextCleaner.clean(getattr(p, field, ""))
                if evidence:
                    block += f" | {label}: {evidence[:480]}"
            if pType == "PRODUCT":
                details = catalog_evidence(p.catalog_details)
                if details:
                    block += f" | Thông tin sản phẩm và biến thể: {details}"
            blocks.append(block)

        return "\n".join(blocks)

    build_product_context = buildProductContext

    @staticmethod
    def buildGenerationPrompt(
        userMessage: str = "",
        understanding: Optional[UnderstandingResult] = None,
        candidates: Optional[List[RerankedCandidate]] = None,
        conversationHistory: str = "",
        **kwargs,
    ) -> str:
        userMsg = userMessage or kwargs.get("user_message", "")
        und = understanding if understanding is not None else kwargs.get("understanding")
        cands = candidates if candidates is not None else kwargs.get("candidates", [])
        convHist = conversationHistory or kwargs.get("conversation_history", "")

        productContext = ContextBuilder.buildProductContext(cands, maxProducts=len(cands), understanding=und)
        safetyWarnings = ingredientSafetyEngine.checkCandidatesSafety(cands, userMsg)
        return buildDermatologyGenerationPrompt(
            userMessage=userMsg,
            understanding=und,
            productContext=productContext,
            conversationHistory=convHist,
            safetyWarnings=safetyWarnings,
        )

    build_generation_prompt = buildGenerationPrompt


class GroundedGenerationService:

    async def generateResponse(
        self,
        userMessage: str = "",
        understanding: Optional[UnderstandingResult] = None,
        candidates: Optional[List[RerankedCandidate]] = None,
        conversationHistory: str = "",
        **kwargs,
    ) -> str:
        tracker = LatencyTracker("generation")
        userMsg = userMessage or kwargs.get("user_message", "")
        und = understanding if understanding is not None else kwargs.get("understanding")
        cands = candidates if candidates is not None else kwargs.get("candidates", [])
        convHist = conversationHistory or kwargs.get("conversation_history", "")

        if not cands:
            return (
                "Hiện tại hệ thống chưa xác nhận được sản phẩm khớp với yêu cầu. "
                "Bạn có thể kiểm tra lại tên hoặc gửi thêm dung tích, thương hiệu hay mức giá mong muốn."
            )

        fields = requested_catalog_fields(userMsg)
        asks_price = bool(re.search(r"giá bao nhiêu|bao nhiêu tiền", userMsg, re.IGNORECASE))
        asks_stock = bool(re.search(r"còn hàng|có hàng không|tồn kho", userMsg, re.IGNORECASE))
        asks_variants = bool(VARIANT_QUESTION.search(TextCleaner.normalize(userMsg, expand_synonyms=True)))
        asks_specs = bool(CATALOG_SPEC_QUESTION.search(userMsg))
        sizes = catalog_sizes(userMsg)
        if fields or asks_price or asks_stock or asks_variants or asks_specs:
            lines = []
            numbered = list(enumerate(cands, 1))
            if asks_variants or asks_specs or sizes:
                query_words = re.findall(r"[^\W_]+", userMsg.lower())
                matches = [(SequenceMatcher(None, query_words, re.findall(r"[^\W_]+", candidate.product.name.lower()))
                            .find_longest_match().size, index) for index, candidate in numbered]
                best = max(score for score, _ in matches)
                if best >= 3 and sum(score == best for score, _ in matches) == 1:
                    match = next(index for score, index in matches if score == best)
                    numbered = [(index, candidate) for index, candidate in numbered if index == match]
            for index, candidate in numbered:
                product = candidate.product
                facts = []
                for field in fields:
                    label = CATALOG_FIELDS[field][0]
                    value = TextCleaner.clean(getattr(product, field, ""))
                    facts.append(f"{label}: {value or 'catalog chưa có thông tin'}")
                variants = product.catalog_details.variants
                if product.targetType == "PRODUCT" and (asks_variants or asks_specs or (sizes and variants)):
                    if asks_specs or not variants:
                        facts.extend(TextCleaner.clean(spec) for spec in product.catalog_details.specifications
                                     if asks_specs or spec.startswith("Dung tích:"))
                    matched = [variant for variant in variants if not sizes or
                               sizes & catalog_sizes(variant.volume or variant.name + " " + " ".join(variant.attributes))]
                    facts.extend(format_variant(variant) for variant in matched)
                    if not matched and (variants or not facts):
                        facts.append("Catalog chưa có thông tin biến thể hoặc quy cách khớp với yêu cầu")
                elif asks_price or asks_stock:
                    facts.append(f"Giá: {product.price:,.0f} VND")
                    if asks_stock and product.targetType == "PRODUCT":
                        facts.append("Còn hàng" if product.inStock else "Hết hàng")
                if facts:
                    lines.append(f"**{product.name}** [{index}] — " + " | ".join(facts))
            if not lines:
                return "Catalog chưa có thông tin phù hợp với yêu cầu."
            return "\n".join(lines + ingredientSafetyEngine.checkCandidatesSafety(cands, userMsg))

        prompt = ContextBuilder.buildGenerationPrompt(
            userMessage=userMsg,
            understanding=und,
            candidates=cands,
            conversationHistory=convHist,
        )

        with tracker.measure("llm_generate"):
            try:
                response = await llmClient.generateText(
                    prompt=prompt,
                    system_instruction=(
                        "Bạn là Chuyên viên Tư vấn Mỹ phẩm & Da liễu Chân thành, Ngắn gọn và Trực diện. "
                        "Tuyệt đối KHÔNG mở đầu sáo rỗng giả trân (như 'rất vui được đồng hành...'). "
                        "Đi thẳng vào trọng tâm, trả lời tối đa 2-3 câu ngắn (dưới 80 từ). "
                        "Trích xuất thông tin liên quan theo đúng câu hỏi (giá, thành phần, cách dùng). "
                        "Chỉ dùng dữ liệu catalog cho nhận định về sản phẩm. Nếu thiếu, nói rõ chưa có dữ liệu. "
                        "BẮT BUỘC ghi số nguồn tương ứng dạng [1], [2] ngay sau tên sản phẩm hoặc thông tin trích dẫn. "
                        "Tuyệt đối KHÔNG sử dụng bất kỳ emoji hay biểu tượng icon nào trong câu trả lời."
                    ),
                    max_tokens=200,
                    temperature=0.3,
                )
                if response:
                    isValid, sanitized, issues = generationGuardrail.verifyResponse(response, cands)
                    if isValid:
                        return remove_emojis(sanitized)
                    logger.warning("Generated response rejected: %s", issues)
            except LLMClientError as exc:
                logger.warning("Lỗi sinh câu trả lời từ LLM: %s. Kích hoạt fallback mẫu có sẵn.", exc)

        safetyWarnings = ingredientSafetyEngine.checkCandidatesSafety(cands, userMsg)
        return self.templateFallback(cands, safetyWarnings=safetyWarnings)

    generate_response = generateResponse

    def templateFallback(
        self,
        candidates: List[RerankedCandidate],
        safetyWarnings: Optional[List[str]] = None,
        **kwargs,
    ) -> str:
        safeWarn = safetyWarnings if safetyWarnings is not None else kwargs.get("safety_warnings")
        lines = [
            "Dưới đây là các lựa chọn phù hợp nhất với tiêu chí của bạn:",
            "",
        ]
        for idx, cand in enumerate(candidates[:3], 1):
            p = cand.product
            relevanceExpl = cand.relevanceExplanation or getattr(cand, "relevance_explanation", "") or p.benefits
            lines.append(f"- **{p.name}** [{idx}] ({p.brand}, {p.price:,.0f} VND): {relevanceExpl}")

        if safeWarn:
            lines.append("")
            lines.append("**Lưu ý an toàn**:")
            for sw in safeWarn:
                lines.append(f"- {sw}")

        lines.append("")
        lines.append("Bạn có cần mình giải thích kỹ hơn về thành phần hay cách dùng của sản phẩm nào ở trên không?")
        return "\n".join(lines)

    _template_fallback = templateFallback

    async def generateStream(
        self,
        userMessage: str = "",
        understanding: Optional[UnderstandingResult] = None,
        candidates: Optional[List[RerankedCandidate]] = None,
        conversationHistory: str = "",
        **kwargs,
    ):
        # Do not expose unverified model text in SSE. The metadata still streams
        # immediately; the verified answer follows as a single text event.
        answer = await self.generateResponse(
            userMessage=userMessage, understanding=understanding, candidates=candidates,
            conversationHistory=conversationHistory, **kwargs,
        )
        yield answer

    generate_stream = generateStream


contextBuilder = ContextBuilder()
context_builder = contextBuilder

generationService = GroundedGenerationService()
generation_service = generationService
