import math
from typing import Any, List, Optional, Set
import pymysql
import pymysql.cursors

from app.core.config import getSettings
from app.core.logging import getLogger
from app.repositories.vector_repository import vectorRepository
from app.services.ingestion.extractor import DatabaseExtractor
from app.services.retrieval.search import embeddingService

logger = getLogger("recommendationService")
settings = getSettings()

ACTION_WEIGHTS = {
    "PURCHASE": 5.0,
    "BUY": 5.0,
    "ADD_TO_CART": 3.0,
    "CART": 3.0,
    "VIEW": 1.0,
    "SEARCH_CLICK": 1.5,
}


class RecommendationService:

    def __init__(self):
        self.vectorRepo = vectorRepository
        self.embeddingSvc = embeddingService
        self.dbExtractor = DatabaseExtractor()

    def getProductVector(self, productId: int) -> Optional[List[float]]:
        """Lấy vector biểu diễn của sản phẩm từ Qdrant hoặc tính toán từ DB nếu chưa có."""
        prodStrId = str(productId)
        # 1. Thử lấy point mô tả tổng quan (hỗ trợ cả prefix mysql_ và không prefix)
        for chunk_key in [f"mysql_{prodStrId}_desc", f"{prodStrId}_desc"]:
            try:
                pointUuid = self.vectorRepo.chunkIdToUuid(chunk_key)
                points = self.vectorRepo.client.retrieve(
                    collection_name=self.vectorRepo.collectionName,
                    ids=[pointUuid],
                    with_vectors=True,
                )
                if points and points[0].vector:
                    return list(points[0].vector)
            except Exception as e:
                logger.debug("Không tìm thấy point UUID chuẩn cho %s: %s", chunk_key, e)

        # 2. Thử cuộn tìm bất kỳ chunk nào của sản phẩm trong Qdrant
        try:
            from qdrant_client.models import FieldCondition, Filter, MatchValue
            for pid_val in [f"mysql_{prodStrId}", prodStrId]:
                records, _ = self.vectorRepo.client.scroll(
                    collection_name=self.vectorRepo.collectionName,
                    scroll_filter=Filter(
                        must=[FieldCondition(key="product_id", match=MatchValue(value=pid_val))]
                    ),
                    limit=1,
                    with_vectors=True,
                )
                if records and records[0].vector:
                    return list(records[0].vector)
        except Exception as e:
            logger.debug("Lỗi scroll vector cho sản phẩm %s: %s", productId, e)

        # 3. Fallback: Lấy text từ MySQL và sinh vector trực tiếp
        try:
            conn = self.dbExtractor.getConnection()
            with conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT p.id, p.name, COALESCE(b.name, '') as brand,
                           COALESCE(p.short_description, '') as short_desc,
                           COALESCE(p.description, '') as description,
                           COALESCE(p.ingredients, '') as ingredients
                    FROM products p
                    LEFT JOIN brands b ON p.brand_id = b.id
                    WHERE p.id = %s AND p.is_deleted = 0
                    """,
                    (productId,),
                )
                row = cur.fetchone()
            conn.close()

            if row:
                textToEmbed = f"{row.get('name', '')} {row.get('brand', '')} {row.get('short_desc', '')} {row.get('description', '')[:300]} {row.get('ingredients', '')[:200]}"
                vector = self.embeddingSvc.getEmbedding(textToEmbed)
                return vector
        except Exception as e:
            logger.error("Lỗi sinh vector từ MySQL cho sản phẩm %s: %s", productId, e)

        return None

    def getSimilarProducts(self, productId: int, limit: int = 8) -> List[int]:
        """Gợi ý Item-to-Item Content-Based: Tìm các sản phẩm có độ tương đồng Cosine cao nhất."""
        targetVector = self.getProductVector(productId)
        if not targetVector:
            logger.warning("Không tìm thấy vector cho sản phẩm %s", productId)
            return []

        try:
            from qdrant_client.models import FieldCondition, Filter, MatchValue

            filterProduct = Filter(
                must=[
                    FieldCondition(key="target_type", match=MatchValue(value="PRODUCT"))
                ]
            )

            # Lấy top kết quả dư để lọc trùng product_id (do 1 sản phẩm có nhiều chunk)
            candidatePoints = self.vectorRepo.searchVectors(
                queryVector=targetVector,
                limit=max(30, limit * 4),
            )

            similarIds: List[int] = []
            seenIds: Set[int] = {productId}

            for pt in candidatePoints:
                pIdRaw = pt.payload.get("product_id") if pt.payload else None
                if pIdRaw is not None:
                    try:
                        pIdStr = str(pIdRaw).replace("mysql_", "").strip()
                        pId = int(pIdStr)
                        if pId not in seenIds:
                            seenIds.add(pId)
                            similarIds.append(pId)
                            if len(similarIds) >= limit:
                                break
                    except (ValueError, TypeError):
                        continue

            return similarIds
        except Exception as e:
            logger.error("Lỗi tính toán similar products cho sản phẩm %s: %s", productId, e)
            return []

    def getPopularProductsFallback(self, limit: int = 8) -> List[int]:
        """Fallback Cold-Start: Lấy danh sách sản phẩm bán chạy/nổi bật nhất từ MySQL."""
        try:
            conn = self.dbExtractor.getConnection()
            with conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT id
                    FROM products
                    WHERE is_deleted = 0 AND status = 'ACTIVE'
                    ORDER BY is_featured DESC, (total_sold * 0.7 + average_rating * 0.3) DESC, id ASC
                    LIMIT %s
                    """,
                    (limit,),
                )
                rows = cur.fetchall()
            conn.close()
            return [int(r["id"]) for r in rows if "id" in r]
        except Exception as e:
            logger.error("Lỗi lấy danh sách sản phẩm phổ biến fallback: %s", e)
            return []

    def getRecommendationsForUser(
        self,
        userId: Optional[int] = None,
        sessionId: Optional[str] = None,
        limit: int = 8,
    ) -> List[int]:
        """Gợi ý User-to-Item Content-Based:
        Tổng hợp User Profile Vector từ lịch sử tương tác có trọng số,
        sau đó truy vấn Qdrant để tìm sản phẩm tương đồng nhất.
        """
        if not userId and not sessionId:
            return self.getPopularProductsFallback(limit)

        # 1. Truy vấn lịch sử tương tác gần nhất từ MySQL (hỗ trợ cả userId và sessionId)
        interactions: List[dict] = []
        try:
            conn = self.dbExtractor.getConnection()
            with conn.cursor() as cur:
                if userId and sessionId:
                    cur.execute(
                        """
                        SELECT product_id, action_type
                        FROM user_product_interactions
                        WHERE user_id = %s OR session_id = %s
                        ORDER BY created_at DESC
                        LIMIT 20
                        """,
                        (userId, sessionId),
                    )
                elif userId:
                    cur.execute(
                        """
                        SELECT product_id, action_type
                        FROM user_product_interactions
                        WHERE user_id = %s
                        ORDER BY created_at DESC
                        LIMIT 20
                        """,
                        (userId,),
                    )
                else:
                    cur.execute(
                        """
                        SELECT product_id, action_type
                        FROM user_product_interactions
                        WHERE session_id = %s
                        ORDER BY created_at DESC
                        LIMIT 20
                        """,
                        (sessionId,),
                    )
                interactions = cur.fetchall()
            conn.close()
        except Exception as e:
            logger.warning("Bảng user_product_interactions chưa sẵn sàng hoặc lỗi: %s", e)
            return self.getPopularProductsFallback(limit)

        if not interactions:
            return self.getPopularProductsFallback(limit)

        # 2. Tính User Profile Vector theo trọng số hành vi
        weightedVectors: List[tuple[List[float], float]] = []
        interactedProductIds: Set[int] = set()

        for inter in interactions:
            try:
                pId = int(inter["product_id"])
                interactedProductIds.add(pId)
                action = str(inter.get("action_type", "VIEW")).upper()
                weight = ACTION_WEIGHTS.get(action, 1.0)
                pVector = self.getProductVector(pId)
                if pVector:
                    weightedVectors.append((pVector, weight))
            except Exception:
                continue

        if not weightedVectors:
            return self.getPopularProductsFallback(limit)

        # Tổng hợp vector người dùng
        dimension = len(weightedVectors[0][0])
        userVector = [0.0] * dimension
        totalWeight = sum(w for _, w in weightedVectors)

        for vec, w in weightedVectors:
            for d in range(dimension):
                userVector[d] += vec[d] * w

        if totalWeight > 0:
            userVector = [val / totalWeight for val in userVector]

        # Chuẩn hóa L2 norm cho user vector
        norm = math.sqrt(sum(v * v for v in userVector))
        if norm > 0:
            userVector = [v / norm for v in userVector]

        # 3. Tìm sản phẩm gần nhất trong Qdrant
        try:
            candidatePoints = self.vectorRepo.searchVectors(
                queryVector=userVector,
                limit=max(30, limit * 4),
            )

            recommendedIds: List[int] = []
            seenIds: Set[int] = set(interactedProductIds)

            for pt in candidatePoints:
                pIdRaw = pt.payload.get("product_id") if pt.payload else None
                if pIdRaw is not None:
                    try:
                        pIdStr = str(pIdRaw).replace("mysql_", "").strip()
                        pId = int(pIdStr)
                        if pId not in seenIds:
                            seenIds.add(pId)
                            recommendedIds.append(pId)
                            if len(recommendedIds) >= limit:
                                break
                    except (ValueError, TypeError):
                        continue

            # Nếu danh sách lọc còn thiếu, bổ sung thêm từ popular
            if len(recommendedIds) < limit:
                fallbacks = self.getPopularProductsFallback(limit)
                for fId in fallbacks:
                    if fId not in seenIds:
                        seenIds.add(fId)
                        recommendedIds.append(fId)
                        if len(recommendedIds) >= limit:
                            break

            return recommendedIds
        except Exception as e:
            logger.error("Lỗi tìm kiếm gợi ý User-to-Item: %s", e)
            return self.getPopularProductsFallback(limit)


recommendationService = RecommendationService()
