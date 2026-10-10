from typing import Any, List, Optional
from fastapi import APIRouter, Query

from app.core.logging import getLogger
from app.services.recommendation import recommendationService

logger = getLogger("recommendApi")

router = APIRouter(prefix="/recommend", tags=["Hệ thống Gợi ý Sản phẩm (RecSys)"])


@router.get(
    "/similar/{product_id}",
    summary="Gợi ý sản phẩm tương tự (Content-Based Item-to-Item)",
    description="Truy vấn Qdrant để tìm các sản phẩm có độ tương đồng ngữ nghĩa và da liễu cao nhất với sản phẩm hiện tại.",
)
async def getSimilarProducts(
    product_id: int,
    limit: int = Query(default=8, ge=1, le=50, description="Số lượng sản phẩm gợi ý"),
) -> dict[str, Any]:
    try:
        similarIds = recommendationService.getSimilarProducts(productId=product_id, limit=limit)
        return {
            "status": "success",
            "product_id": product_id,
            "recommended_product_ids": similarIds,
            "total": len(similarIds),
            "algorithm": "CONTENT_BASED_COSINE_SIMILARITY",
        }
    except Exception as e:
        logger.error("Lỗi API getSimilarProducts: %s", e)
        return {
            "status": "error",
            "product_id": product_id,
            "recommended_product_ids": [],
            "total": 0,
            "detail": str(e),
        }


@router.get(
    "/for-you",
    summary="Gợi ý sản phẩm dành cho bạn (Content-Based User-to-Item & Cold-Start Fallback)",
    description="Tổng hợp vector hồ sơ người dùng từ lịch sử tương tác (View/Cart/Purchase) và tìm các sản phẩm phù hợp nhất.",
)
async def getRecommendationsForUser(
    user_id: Optional[int] = Query(default=None, description="ID người dùng đã đăng nhập"),
    session_id: Optional[str] = Query(default=None, description="Mã phiên ẩn danh (Guest Session ID)"),
    limit: int = Query(default=8, ge=1, le=50, description="Số lượng sản phẩm gợi ý"),
) -> dict[str, Any]:
    try:
        recommendedIds = recommendationService.getRecommendationsForUser(
            userId=user_id,
            sessionId=session_id,
            limit=limit,
        )
        return {
            "status": "success",
            "user_id": user_id,
            "session_id": session_id,
            "recommended_product_ids": recommendedIds,
            "total": len(recommendedIds),
            "algorithm": "USER_PROFILE_WEIGHTED_CENTROID_COSINE",
        }
    except Exception as e:
        logger.error("Lỗi API getRecommendationsForUser: %s", e)
        return {
            "status": "error",
            "user_id": user_id,
            "session_id": session_id,
            "recommended_product_ids": [],
            "total": 0,
            "detail": str(e),
        }
