import uuid
from typing import Any, List, Optional
from qdrant_client import QdrantClient
from qdrant_client.models import (
    Distance,
    FieldCondition,
    Filter,
    MatchAny,
    MatchValue,
    PointStruct,
    Range,
    ScoredPoint,
    VectorParams,
)

from app.core.config import getSettings
from app.core.logging import getLogger
from app.schemas.product import ProductChunk

logger = getLogger("vector_repository")
settings = getSettings()


class VectorRepository:
    def __init__(
        self,
        url: Optional[str] = None,
        apiKey: Optional[str] = None,
        collectionName: Optional[str] = None,
        dimension: Optional[int] = None,
        **kwargs,
    ):
        apiKey = kwargs.get("api_key", apiKey)
        collectionName = kwargs.get("collection_name", collectionName)
        self.url = url or settings.qdrant_url
        self.apiKey = apiKey or (settings.qdrant_api_key if settings.qdrant_api_key else None)
        self.api_key = self.apiKey
        self.collectionName = collectionName or settings.qdrant_collection
        self.collection_name = self.collectionName
        self.dimension = dimension or settings.vector_dimension

        if self.url == ":memory:":
            self.client = QdrantClient(":memory:")
        elif self.url.startswith("http://") or self.url.startswith("https://"):
            self.client = QdrantClient(url=self.url, api_key=self.apiKey)
        else:
            self.client = QdrantClient(path=self.url)

        self.initCollection()

    def initCollection(self) -> None:
        try:
            collections = self.client.get_collections().collections
            exists = any(c.name == self.collectionName for c in collections)
            if not exists:
                self.client.create_collection(
                    collection_name=self.collectionName,
                    vectors_config=VectorParams(
                        size=self.dimension,
                        distance=Distance.COSINE,
                    ),
                )
        except Exception:
            raise

    _init_collection = initCollection

    def chunkIdToUuid(self, chunkId: str, **kwargs) -> str:
        chunkId = kwargs.get("chunk_id", chunkId)
        return str(uuid.uuid5(uuid.NAMESPACE_DNS, chunkId))

    _chunk_id_to_uuid = chunkIdToUuid

    def upsertChunks(self, chunks: List[ProductChunk], vectors: List[List[float]]) -> int:
        if not chunks or not vectors or len(chunks) != len(vectors):
            return 0

        points: List[PointStruct] = []
        for chunk, vec in zip(chunks, vectors):
            pointId = self.chunkIdToUuid(chunk.chunk_id)
            payload = {
                "chunk_id": chunk.chunk_id,
                "product_id": chunk.product_id,
                "chunk_type": chunk.chunk_type.value,
                "content": chunk.content,
                "category": chunk.metadata.category,
                "brand": chunk.metadata.brand,
                "skin_type": chunk.metadata.skin_type,
                "concerns": chunk.metadata.concerns,
                "price": chunk.metadata.price,
                "rating": chunk.metadata.rating,
                "total_reviews": getattr(chunk.metadata, "totalReviews", 0) or getattr(chunk.metadata, "total_reviews", 0),
                "total_sold": getattr(chunk.metadata, "totalSold", 0) or getattr(chunk.metadata, "total_sold", 0),
                "target_type": getattr(chunk.metadata, "targetType", "PRODUCT") or getattr(chunk.metadata, "target_type", "PRODUCT") or "PRODUCT",
                "duration_minutes": getattr(chunk.metadata, "durationMinutes", 0) or getattr(chunk.metadata, "duration_minutes", 0) or 0,
            }
            points.append(
                PointStruct(
                    id=pointId,
                    vector=vec,
                    payload=payload,
                )
            )

        self.client.upsert(
            collection_name=self.collectionName,
            points=points,
        )
        return len(points)

    upsert_chunks = upsertChunks
    save_chunks_batch = upsertChunks
    saveChunksBatch = upsertChunks

    def searchVectors(
        self,
        queryVector: Optional[List[float]] = None,
        limit: int = 20,
        category: Optional[str] = None,
        brand: Optional[str] = None,
        minPrice: Optional[float] = None,
        maxPrice: Optional[float] = None,
        skinType: Optional[str] = None,
        concerns: Optional[List[str]] = None,
        targetType: Optional[str] = None,
        **kwargs,
    ) -> List[ScoredPoint]:
        queryVector = queryVector or kwargs.get("query_vector")
        if queryVector is None:
            raise ValueError("queryVector is required")
        minPrice = minPrice if minPrice is not None else kwargs.get("min_price")
        maxPrice = maxPrice if maxPrice is not None else kwargs.get("max_price")
        skinType = skinType or kwargs.get("skin_type")
        targetType = targetType or kwargs.get("target_type")

        mustConditions = []

        if targetType:
            mustConditions.append(
                FieldCondition(key="target_type", match=MatchValue(value=targetType.upper().strip()))
            )

        if category:
            catClean = category.strip()
            mustConditions.append(
                FieldCondition(
                    key="category",
                    match=MatchAny(any=[catClean, catClean.lower(), catClean.capitalize()])
                )
            )

        if brand:
            mustConditions.append(
                FieldCondition(key="brand", match=MatchValue(value=brand.strip()))
            )

        if skinType:
            mustConditions.append(
                FieldCondition(
                    key="skin_type",
                    match=MatchAny(any=[skinType.lower().strip(), "all", "mọi loại da"])
                )
            )

        if concerns:
            cleanConcerns = [c.lower().strip() for c in concerns if c.strip()]
            if cleanConcerns:
                mustConditions.append(
                    FieldCondition(key="concerns", match=MatchAny(any=cleanConcerns))
                )

        if minPrice is not None or maxPrice is not None:
            rangeKwargs: dict[str, Any] = {}
            if minPrice is not None:
                rangeKwargs["gte"] = float(minPrice)
            if maxPrice is not None:
                rangeKwargs["lte"] = float(maxPrice)
            mustConditions.append(
                FieldCondition(key="price", range=Range(**rangeKwargs))
            )

        queryFilter = Filter(must=mustConditions) if mustConditions else None

        def doSearch(flt: Optional[Filter]) -> List[ScoredPoint]:
            if hasattr(self.client, "query_points"):
                resp = self.client.query_points(
                    collection_name=self.collectionName,
                    query=queryVector,
                    query_filter=flt,
                    limit=limit,
                )
                return list(resp.points)
            else:
                return list(self.client.search(
                    collection_name=self.collectionName,
                    query_vector=queryVector,
                    query_filter=flt,
                    limit=limit,
                ))

        points = doSearch(queryFilter)

        if not points and mustConditions:
            relaxedConditions = []
            if category:
                catClean = category.strip()
                relaxedConditions.append(
                    FieldCondition(
                        key="category",
                        match=MatchAny(any=[catClean, catClean.lower(), catClean.capitalize()]),
                    )
                )
            relaxedFilter = Filter(must=relaxedConditions) if relaxedConditions else None
            points = doSearch(relaxedFilter)

        return points

    search_vectors = searchVectors

    def deleteByProductId(self, productId: str, **kwargs) -> None:
        productId = kwargs.get("product_id", productId)
        self.client.delete(
            collection_name=self.collectionName,
            points_selector=Filter(
                must=[
                    FieldCondition(key="product_id", match=MatchValue(value=str(productId)))
                ]
            ),
        )

    delete_by_product_id = deleteByProductId


vectorRepository = VectorRepository()
vector_repository = vectorRepository
