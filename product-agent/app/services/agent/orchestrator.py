import json
from typing import Any, AsyncGenerator, List, Optional

from app.core.logging import LatencyTracker, getLogger
from app.llm.client import llmClient
from app.schemas.chat import ChatRequest, ChatResponse, ProductCard, SourceRef
from app.schemas.query import UnderstandingResult
from app.schemas.retrieval import RerankedCandidate
from app.services.agent.dialogue import conversationManager
from app.services.agent.generation import generationService
from app.services.agent.router import fastPathParser, queryRouter
from app.services.ingestion.cleaning import remove_emojis
from app.services.retrieval.reranker import rerankerService
from app.services.retrieval.search import retrievalService

logger = getLogger("agentOrchestrator")


class PipelinePreparedContext:

    def __init__(
        self,
        sessionId: Optional[str] = None,
        mergedUnderstanding: Optional[UnderstandingResult] = None,
        history: str = "",
        isRetrieval: bool = True,
        nonRetrievalReply: Optional[str] = None,
        rerankedCandidates: Optional[List[RerankedCandidate]] = None,
        **kwargs: Any,
    ):
        self.sessionId = sessionId or kwargs.get("session_id", "")
        self.mergedUnderstanding = mergedUnderstanding or kwargs.get("merged_understanding")
        self.history = history
        if "isRetrieval" in kwargs:
            self.isRetrieval = kwargs["isRetrieval"]
        elif "is_retrieval" in kwargs:
            self.isRetrieval = kwargs["is_retrieval"]
        else:
            self.isRetrieval = isRetrieval
        self.nonRetrievalReply = nonRetrievalReply or kwargs.get("non_retrieval_reply")
        self.rerankedCandidates = rerankedCandidates or kwargs.get("reranked_candidates")

    @property
    def session_id(self) -> str:
        return self.sessionId

    @session_id.setter
    def session_id(self, value: str) -> None:
        self.sessionId = value

    @property
    def merged_understanding(self) -> Optional[UnderstandingResult]:
        return self.mergedUnderstanding

    @merged_understanding.setter
    def merged_understanding(self, value: Optional[UnderstandingResult]) -> None:
        self.mergedUnderstanding = value

    @property
    def is_retrieval(self) -> bool:
        return self.isRetrieval

    @is_retrieval.setter
    def is_retrieval(self, value: bool) -> None:
        self.isRetrieval = value

    @property
    def non_retrieval_reply(self) -> Optional[str]:
        return self.nonRetrievalReply

    @non_retrieval_reply.setter
    def non_retrieval_reply(self, value: Optional[str]) -> None:
        self.nonRetrievalReply = value

    @property
    def reranked_candidates(self) -> Optional[List[RerankedCandidate]]:
        return self.rerankedCandidates

    @reranked_candidates.setter
    def reranked_candidates(self, value: Optional[List[RerankedCandidate]]) -> None:
        self.rerankedCandidates = value


class AgentOrchestrator:

    @staticmethod
    def formatProductCards(candidates: List[RerankedCandidate]) -> List[ProductCard]:
        cards: List[ProductCard] = []
        for cand in candidates:
            p = cand.product
            cards.append(
                ProductCard(
                    id=p.id,
                    name=p.name,
                    brand=p.brand,
                    category=p.category,
                    price=p.price,
                    skinType=getattr(p, "skinType", getattr(p, "skin_type", None)),
                    concerns=p.concerns,
                    keyIngredients=p.ingredients[:100] + "..." if len(p.ingredients) > 100 else p.ingredients,
                    rating=p.rating,
                    totalReviews=getattr(p, "totalReviews", getattr(p, "total_reviews", 0)) or 0,
                    totalSold=getattr(p, "totalSold", getattr(p, "total_sold", 0)) or 0,
                    reasonForRecommendation=cand.relevanceExplanation or getattr(cand, "relevance_explanation", "") or "",
                    imageUrl=getattr(p, "imageUrl", getattr(p, "image_url", None)),
                    targetType=getattr(p, "targetType", getattr(p, "target_type", "PRODUCT")) or "PRODUCT",
                    durationMinutes=getattr(p, "durationMinutes", getattr(p, "duration_minutes", 0)) or 0,
                )
            )
        return cards

    format_product_cards = formatProductCards

    @staticmethod
    def formatSources(candidates: List[RerankedCandidate]) -> List[SourceRef]:
        sources: List[SourceRef] = []
        for cand in candidates:
            p = cand.product
            sources.append(
                SourceRef(
                    productId=p.id,
                    productName=p.name,
                    chunkType="product_details",
                    snippet=f"{p.brand} - {p.name}: {p.benefits[:120]}...",
                )
            )
        return sources

    format_sources = formatSources

    async def preparePipeline(
        self,
        request: ChatRequest,
        tracker: LatencyTracker,
    ) -> PipelinePreparedContext:
        reqSessionId = request.sessionId or getattr(request, "session_id", "")
        ctx = conversationManager.getOrCreate(reqSessionId)
        fastResult = fastPathParser.tryParse(request.message, ctx)

        if fastResult is not None:
            with tracker.measure("fast_path_understanding"):
                understanding = fastResult
                logger.info("Khớp mẫu xử lý nhanh Fast-path cho tin nhắn: '%s'", request.message)
        else:
            refRes = ctx.entityMemory.resolveReference(request.message)
            activeEnt = ctx.entityMemory.getActiveEntity()

            currentState = None
            if (
                ctx.accumulatedCategory
                or ctx.accumulatedConstraints
                or ctx.accumulatedPreferences
                or activeEnt
                or refRes.hasReference
                or len(ctx.turns) > 0
            ):
                currentState = {
                    "category": ctx.accumulatedCategory,
                    "constraints": dict(ctx.accumulatedConstraints),
                    "preferences": dict(ctx.accumulatedPreferences),
                }
                if len(ctx.turns) > 0:
                    lastTurn = ctx.turns[-1]
                    currentState["last_user_message"] = lastTurn.userMessage
                    currentState["last_bot_response"] = lastTurn.botResponse
                if activeEnt:
                    currentState["active_focus_product"] = activeEnt.productName
                    currentState["active_focus_brand"] = activeEnt.brand
                    currentState["active_focus_category"] = activeEnt.category
                if refRes.hasReference and refRes.referencedEntity:
                    currentState["referenced_entity"] = {
                        "product_name": refRes.referencedEntity.productName,
                        "brand": refRes.referencedEntity.brand,
                        "category": refRes.referencedEntity.category,
                        "reference_type": refRes.referenceType,
                        "matched_phrase": refRes.matchedPhrase,
                    }

            with tracker.measure("query_understanding"):
                understanding = await llmClient.understand(
                    message=request.message,
                    currentState=currentState,
                )

        with tracker.measure("context_merge"):
            mergedUnderstanding = conversationManager.mergeUnderstanding(
                sessionId=reqSessionId,
                current=understanding,
            )
            history = conversationManager.getHistoryFormatted(reqSessionId)

        if not queryRouter.shouldRetrieve(mergedUnderstanding):
            clarificationQ = mergedUnderstanding.clarificationQuestion or getattr(mergedUnderstanding, "clarification_question", None)
            needsClarify = mergedUnderstanding.needsClarification or getattr(mergedUnderstanding, "needs_clarification", False)
            replyText = queryRouter.handleNonRetrieval(mergedUnderstanding) or (
                clarificationQ
                if needsClarify and clarificationQ
                else "Đã nhận yêu cầu đang chuyển tìm kiếm sản phẩm"
            )
            replyText = remove_emojis(replyText)
            return PipelinePreparedContext(
                sessionId=reqSessionId,
                mergedUnderstanding=mergedUnderstanding,
                history=history,
                isRetrieval=False,
                nonRetrievalReply=replyText,
            )

        with tracker.measure("hybrid_retrieval"):
            retrievalRes = await retrievalService.retrieve(mergedUnderstanding, topK=15)

        with tracker.measure("reranker"):
            uIntent = getattr(mergedUnderstanding.intent, "value", str(mergedUnderstanding.intent))
            uQty = getattr(mergedUnderstanding, "targetQuantity", getattr(mergedUnderstanding, "target_quantity", None))
            reqMsgL = request.message.lower()
            if uQty and uQty > 0:
                rerankLimit = uQty
            elif any(p in reqMsgL for p in ["chỉ 1", "chỉ một", "1 sản phẩm", "một sản phẩm", "duy nhất 1", "duy nhất một"]):
                rerankLimit = 1
            else:
                rerankLimit = 1 if uIntent == "PRODUCT_DETAIL" else (
                    2 if uIntent in ("COMPARISON", "PRODUCT_COMPARISON") else (
                        4 if uIntent in ("ROUTINE_RECOMMENDATION", "ROUTINE") else 3
                    )
                )

            rerankedCandidates = rerankerService.rerank(
                candidates=retrievalRes.candidates,
                understanding=mergedUnderstanding,
                topK=rerankLimit,
            )

        return PipelinePreparedContext(
            sessionId=reqSessionId,
            mergedUnderstanding=mergedUnderstanding,
            history=history,
            isRetrieval=True,
            rerankedCandidates=rerankedCandidates,
        )

    _prepare_pipeline = preparePipeline

    async def handleChat(self, request: ChatRequest) -> ChatResponse:
        tracker = LatencyTracker("chat_turn")
        prep = await self.preparePipeline(request, tracker)
        reqSessionId = request.sessionId or getattr(request, "session_id", "")

        if not prep.isRetrieval:
            conversationManager.recordTurn(
                sessionId=reqSessionId,
                userMessage=request.message,
                botResponse=prep.nonRetrievalReply or "",
                understanding=prep.mergedUnderstanding,
            )
            intentVal = (
                prep.mergedUnderstanding.intent.value
                if hasattr(prep.mergedUnderstanding.intent, "value")
                else str(prep.mergedUnderstanding.intent)
            )
            return ChatResponse(
                sessionId=reqSessionId,
                intent=intentVal,
                answer=prep.nonRetrievalReply or "",
                message=prep.nonRetrievalReply or "",
                needsClarification=prep.mergedUnderstanding.needsClarification,
                understanding=prep.mergedUnderstanding,
                stageLatenciesMs=tracker.finish(),
            )

        candidates = prep.rerankedCandidates or []
        with tracker.measure("generation"):
            answer = await generationService.generateResponse(
                userMessage=request.message,
                understanding=prep.mergedUnderstanding,
                candidates=candidates,
                conversationHistory=prep.history,
            )
            answer = remove_emojis(answer)

        productCards = self.formatProductCards(candidates)
        sources = self.formatSources(candidates)

        conversationManager.recordTurn(
            sessionId=reqSessionId,
            userMessage=request.message,
            botResponse=answer,
            understanding=prep.mergedUnderstanding,
            candidates=candidates,
        )

        intentVal = (
            prep.mergedUnderstanding.intent.value
            if hasattr(prep.mergedUnderstanding.intent, "value")
            else str(prep.mergedUnderstanding.intent)
        )
        return ChatResponse(
            sessionId=reqSessionId,
            intent=intentVal,
            answer=answer,
            message=answer,
            products=productCards,
            sources=sources,
            needsClarification=False,
            understanding=prep.mergedUnderstanding,
            stageLatenciesMs=tracker.finish(),
        )

    handle_chat = handleChat

    async def handleChatStream(self, request: ChatRequest) -> AsyncGenerator[str, None]:
        tracker = LatencyTracker("chat_turn_stream")
        reqSessionId = request.sessionId or getattr(request, "session_id", "")
        try:
            prep = await self.preparePipeline(request, tracker)

            if not prep.isRetrieval:
                replyText = prep.nonRetrievalReply or ""
                conversationManager.recordTurn(
                    sessionId=reqSessionId,
                    userMessage=request.message,
                    botResponse=replyText,
                    understanding=prep.mergedUnderstanding,
                )
                yield f"data: {json.dumps({'type': 'metadata', 'products': [], 'stage_latencies_ms': tracker.finish()}, ensure_ascii=False)}\n\n"
                yield f"data: {json.dumps({'type': 'token', 'content': replyText}, ensure_ascii=False)}\n\n"
                yield f"data: {json.dumps({'type': 'done', 'session_id': reqSessionId}, ensure_ascii=False)}\n\n"
                return

            candidates = prep.rerankedCandidates or []
            cards = [{**c.model_dump(), **c.model_dump(by_alias=True)} for c in self.formatProductCards(candidates)]
            yield f"data: {json.dumps({'type': 'metadata', 'products': cards, 'stage_latencies_ms': tracker.getDurations()}, ensure_ascii=False)}\n\n"

            fullTextChunks = []
            async for token in generationService.generateStream(
                userMessage=request.message,
                understanding=prep.mergedUnderstanding,
                candidates=candidates,
                conversationHistory=prep.history,
            ):
                fullTextChunks.append(token)
                yield f"data: {json.dumps({'type': 'token', 'content': token}, ensure_ascii=False)}\n\n"

            completeAnswer = remove_emojis("".join(fullTextChunks))
            conversationManager.recordTurn(
                sessionId=reqSessionId,
                userMessage=request.message,
                botResponse=completeAnswer,
                understanding=prep.mergedUnderstanding,
                candidates=candidates,
            )

            yield f"data: {json.dumps({'type': 'done', 'session_id': reqSessionId}, ensure_ascii=False)}\n\n"

        except Exception as exc:
            logger.error("Lỗi trong quá trình truyền dòng: %s", exc)
            yield f"data: {json.dumps({'type': 'error', 'detail': str(exc)}, ensure_ascii=False)}\n\n"

    handle_chat_stream = handleChatStream


agentOrchestrator = AgentOrchestrator()
agent_orchestrator = agentOrchestrator
