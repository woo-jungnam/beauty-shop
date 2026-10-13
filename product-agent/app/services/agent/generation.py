from typing import List, Optional

from app.core.logging import LatencyTracker, getLogger
from app.core.safety import ingredientSafetyEngine
from app.llm.client import LLMClientError, llmClient
from app.llm.prompts import buildDermatologyGenerationPrompt
from app.schemas.query import UnderstandingResult
from app.schemas.retrieval import RerankedCandidate
from app.services.agent.guardrail import generationGuardrail
from app.services.ingestion.cleaning import remove_emojis

logger = getLogger("generationService")


class ContextBuilder:

    @staticmethod
    def buildProductContext(
        candidates: List[RerankedCandidate],
        maxProducts: int = 5,
        **kwargs,
    ) -> str:
        maxProds = maxProducts if "maxProducts" in kwargs or maxProducts != 5 else kwargs.get("max_products", maxProducts)
        if not candidates:
            return "Không tìm thấy sản phẩm nào phù hợp với các tiêu chí tìm kiếm."

        blocks: List[str] = []
        for idx, cand in enumerate(candidates[:maxProds], 1):
            p = cand.product
            priceFmt = f"{p.price:,.0f} VND"
            skins = ", ".join(p.skinType) if p.skinType else "Mọi loại da"
            concerns = ", ".join(p.concerns) if p.concerns else "Không chỉ định"
            reviewsCnt = getattr(p, "totalReviews", 0) or getattr(p, "total_reviews", 0) or 0
            soldCnt = getattr(p, "totalSold", 0) or getattr(p, "total_sold", 0) or 0
            pType = getattr(p, "targetType", "PRODUCT") or getattr(p, "target_type", "PRODUCT") or "PRODUCT"
            duration = getattr(p, "durationMinutes", 0) or getattr(p, "duration_minutes", 0) or 0

            ingr = (p.ingredients[:250] + "...") if len(p.ingredients) > 250 else p.ingredients
            ben = (p.benefits[:250] + "...") if len(p.benefits) > 250 else p.benefits
            usg = (p.usage[:200] + "...") if len(p.usage) > 200 else p.usage

            relevanceExpl = cand.relevanceExplanation or getattr(cand, "relevance_explanation", "") or "Phù hợp với nhu cầu của bạn"

            if pType == "SERVICE":
                durationStr = f" | **Thời lượng**: {duration} phút" if duration else ""
                block = (
                    f"### [Dịch vụ Spa {idx}]: {p.name}\n"
                    f"- **Phân loại**: Dịch vụ Spa / Liệu trình làm đẹp | **Cơ sở**: {p.brand} | **Danh mục**: {p.category}{durationStr}\n"
                    f"- **Giá dịch vụ niêm yết**: {priceFmt}\n"
                    f"- **Đánh giá khách hàng**: {p.rating} sao ({reviewsCnt:,} đánh giá, {soldCnt:,} lượt phục vụ)\n"
                    f"- **Chỉ định loại da**: {skins}\n"
                    f"- **Tình trạng giải quyết**: {concerns}\n"
                    f"- **Công nghệ & Tinh chất sử dụng**: {ingr}\n"
                    f"- **Lợi ích & Hiệu quả mang lại**: {ben}\n"
                    f"- **Quy trình thực hiện chi tiết**: {usg}\n"
                    f"- **Điểm tương thích nổi bật**: {relevanceExpl}"
                )
            else:
                block = (
                    f"### [Sản phẩm {idx}]: {p.name}\n"
                    f"- **Thương hiệu**: {p.brand} | **Danh mục**: {p.category}\n"
                    f"- **Giá niêm yết**: {priceFmt}\n"
                    f"- **Đánh giá tín nhiệm**: {p.rating} sao ({reviewsCnt:,} đánh giá, {soldCnt:,} đã bán)\n"
                    f"- **Chỉ định loại da**: {skins}\n"
                    f"- **Giải quyết vấn đề**: {concerns}\n"
                    f"- **Thành phần cốt lõi**: {ingr}\n"
                    f"- **Công dụng & Lợi ích chính**: {ben}\n"
                    f"- **Cách dùng khuyến nghị**: {usg}\n"
                    f"- **Điểm tương thích nổi bật**: {relevanceExpl}"
                )
            blocks.append(block)

        return "\n\n".join(blocks)

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

        productContext = ContextBuilder.buildProductContext(cands)
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
                "Hiện tại hệ thống chưa tìm thấy sản phẩm nào khớp hoàn toàn với tiêu chí bạn đưa ra. "
                "Bạn có muốn thử điều chỉnh tầm giá hoặc tham khảo các dòng sản phẩm cùng công dụng khác không ạ?"
            )

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
                        "Đi thẳng vào trọng tâm vấn đề, giải thích súc tích và tôn trọng thời gian của khách hàng. "
                        "Giao diện đã có sẵn Thẻ sản phẩm (ảnh, giá, sao), do đó bạn không lặp lại giá hay sao trong văn bản mà chỉ nêu lý do then chốt vì sao sản phẩm phù hợp. "
                        "Tuyệt đối KHÔNG sử dụng bất kỳ emoji hay biểu tượng icon nào trong câu trả lời."
                    ),
                    max_tokens=600,
                    temperature=0.3,
                )
                if response:
                    isValid, sanitized, issues = generationGuardrail.verifyResponse(response, cands)
                    return remove_emojis(sanitized)
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
            lines.append(f"- **{p.name}** ({p.brand}): {relevanceExpl}")

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
        userMsg = userMessage or kwargs.get("user_message", "")
        und = understanding if understanding is not None else kwargs.get("understanding")
        cands = candidates if candidates is not None else kwargs.get("candidates", [])
        convHist = conversationHistory or kwargs.get("conversation_history", "")

        if not cands:
            yield "Rất tiếc, hiện tại hệ thống chưa tìm thấy sản phẩm nào khớp hoàn toàn với tiêu chí bạn đưa ra. Bạn có muốn thử nới lỏng ngân sách hoặc tham khảo các dòng sản phẩm cùng công dụng khác không ạ?"
            return

        prompt = ContextBuilder.buildGenerationPrompt(
            userMessage=userMsg,
            understanding=und,
            candidates=cands,
            conversationHistory=convHist,
        )

        hasTokens = False
        try:
            async for token in llmClient.streamText(
                prompt=prompt,
                system_instruction=(
                    "Bạn là Chuyên viên Tư vấn Mỹ phẩm & Da liễu Chân thành, Ngắn gọn và Trực diện. "
                    "Tuyệt đối KHÔNG mở đầu sáo rỗng giả trân (như 'rất vui được đồng hành...'). "
                    "Đi thẳng vào trọng tâm vấn đề, giải thích súc tích và tôn trọng thời gian của khách hàng. "
                    "Giao diện đã có sẵn Thẻ sản phẩm (ảnh, giá, sao), do đó bạn không lặp lại giá hay sao trong văn bản mà chỉ nêu lý do then chốt vì sao sản phẩm phù hợp. "
                    "Tuyệt đối KHÔNG sử dụng bất kỳ emoji hay biểu tượng icon nào trong câu trả lời."
                ),
                max_tokens=600,
                temperature=0.3,
            ):
                cleaned_token = remove_emojis(token)
                if cleaned_token:
                    hasTokens = True
                    yield cleaned_token
        except Exception as exc:
            logger.warning("Lỗi khi truyền dòng LLM: %s. Chuyển sang phản hồi fallback.", exc)
            sw = ingredientSafetyEngine.checkCandidatesSafety(cands, userMsg)
            yield self.templateFallback(cands, safetyWarnings=sw)
            return

        if not hasTokens:
            sw = ingredientSafetyEngine.checkCandidatesSafety(cands, userMsg)
            yield self.templateFallback(cands, safetyWarnings=sw)

    generate_stream = generateStream


contextBuilder = ContextBuilder()
context_builder = contextBuilder

generationService = GroundedGenerationService()
generation_service = generationService
