from typing import List
from fastapi import APIRouter, HTTPException, status
from fastapi.responses import StreamingResponse

from app.core.logging import getLogger
from app.llm.client import (
    LLMAPIError,
    LLMAuthenticationError,
    LLMClientError,
    LLMResponseValidationError,
    LLMTimeoutError,
    llmClient,
)
from app.schemas import (
    ChatRequest,
    ChatResponse,
    ProductCard,
    RerankedCandidate,
    SourceRef,
    UnderstandTestRequest,
    UnderstandingResult,
)
from app.services import agentOrchestrator, dbExtractor

router = APIRouter(tags=["Tư vấn sản phẩm"])
logger = getLogger("chatApi")


def formatProductCards(candidates: List[RerankedCandidate]) -> List[ProductCard]:
    return agentOrchestrator.formatProductCards(candidates)


_format_product_cards = formatProductCards


def formatSources(candidates: List[RerankedCandidate]) -> List[SourceRef]:
    return agentOrchestrator.formatSources(candidates)


_format_sources = formatSources


@router.post(
    "/sync/database",
    summary="Đồng bộ dữ liệu từ MySQL (Read-Only)",
    description="Truy xuất dữ liệu sản phẩm từ MySQL chính ở chế độ chỉ đọc và nạp vào hệ thống RAG",
)
async def syncDatabaseEndpoint(limit: int | None = None) -> dict:
    try:
        result = await dbExtractor.syncMysqlToRag(limit=limit)
        return {"status": "ok", "result": result}
    except Exception as exc:
        logger.error("Đồng bộ cơ sở dữ liệu thất bại: %s", exc)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Lỗi đồng bộ database: {str(exc)}",
        )


sync_database_endpoint = syncDatabaseEndpoint


@router.post(
    "/test/understand",
    response_model=UnderstandingResult,
    summary="Kiểm tra hiểu truy vấn",
    description="Phân tích tin nhắn người dùng và trích xuất kết quả theo cấu trúc NLU 6 tầng",
)
async def testUnderstand(request: UnderstandTestRequest) -> UnderstandingResult:
    try:
        reqCurrentState = request.currentState or getattr(request, "current_state", None)
        return await llmClient.understand(
            message=request.message,
            currentState=reqCurrentState,
        )
    except LLMAuthenticationError as exc:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Lỗi xác thực LLM") from exc
    except LLMTimeoutError as exc:
        raise HTTPException(status_code=status.HTTP_504_GATEWAY_TIMEOUT, detail="Hết thời gian phản hồi LLM") from exc
    except (LLMAPIError, LLMResponseValidationError) as exc:
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail="Lỗi dịch vụ LLM") from exc
    except LLMClientError as exc:
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Lỗi hệ thống LLM") from exc


test_understand = testUnderstand


@router.post(
    "/chat",
    response_model=ChatResponse,
    summary="Trò chuyện tư vấn sản phẩm",
    description="Hội thoại tư vấn sản phẩm RAG đa tầng (Non-streaming)",
)
async def chatEndpoint(request: ChatRequest) -> ChatResponse:
    try:
        return await agentOrchestrator.handleChat(request)
    except LLMAuthenticationError as exc:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Lỗi xác thực LLM") from exc
    except LLMTimeoutError as exc:
        raise HTTPException(status_code=status.HTTP_504_GATEWAY_TIMEOUT, detail="Hết thời gian phản hồi LLM") from exc
    except (LLMAPIError, LLMResponseValidationError) as exc:
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail="Lỗi dịch vụ LLM") from exc
    except LLMClientError as exc:
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Lỗi hệ thống LLM") from exc


chat_endpoint = chatEndpoint


@router.post(
    "/chat/stream",
    summary="Trò chuyện tư vấn sản phẩm (Streaming SSE)",
    description="Hội thoại tư vấn sản phẩm streaming Server-Sent Events (SSE) với độ trễ phản hồi cực thấp",
)
async def chatStreamEndpoint(request: ChatRequest) -> StreamingResponse:
    return StreamingResponse(
        agentOrchestrator.handleChatStream(request),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


chat_stream_endpoint = chatStreamEndpoint
