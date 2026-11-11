import logging
from pathlib import Path
from typing import Any, Dict, List, Optional
import uuid

from qdrant_client import QdrantClient
from qdrant_client.models import (
    Distance,
    FieldCondition,
    Filter,
    MatchValue,
    PointStruct,
    ScoredPoint,
    VectorParams,
)

logger = logging.getLogger("qdrant_image_store")


class QdrantImageStore:
    def __init__(self, storage_path: str, collection_name: str = "product_images", dimension: int = 512):
        self.storage_path = storage_path
        self.collection_name = collection_name
        self.dimension = dimension

        if storage_path.startswith("http://") or storage_path.startswith("https://"):
            self.client = QdrantClient(url=storage_path)
        else:
            path = Path(storage_path)
            path.mkdir(parents=True, exist_ok=True)
            self.client = QdrantClient(path=str(path))

        self.init_collection()

    def init_collection(self):
        try:
            collections = self.client.get_collections().collections
            exists = any(c.name == self.collection_name for c in collections)
            if not exists:
                logger.info("Khoi tao Qdrant collection moi cho anh: %s (dim=%d)", self.collection_name, self.dimension)
                self.client.create_collection(
                    collection_name=self.collection_name,
                    vectors_config=VectorParams(
                        size=self.dimension,
                        distance=Distance.COSINE,
                    ),
                )
            else:
                logger.info("Qdrant collection '%s' da ton tai", self.collection_name)
        except Exception as exc:
            logger.error("Loi khi khoi tao Qdrant collection: %s", exc)
            raise

    @staticmethod
    def make_point_id(product_id: int, image_id: int) -> str:
        """Tao point_id UUID co dinh tu product_id va image_id."""
        raw_key = f"prod:{product_id}:img:{image_id}"
        return str(uuid.uuid5(uuid.NAMESPACE_DNS, raw_key))

    def upsert_image(
        self,
        product_id: int,
        image_id: int,
        image_url: str,
        vector: List[float],
        product_status: str = "ACTIVE",
        is_deleted: bool = False,
        is_thumbnail: bool = False,
        product_name: str = "",
        category_ids: Optional[List[int]] = None,
        brand_id: Optional[int] = None,
    ):
        point_id = self.make_point_id(product_id, image_id)
        payload = {
            "product_id": int(product_id),
            "image_id": int(image_id),
            "image_url": image_url,
            "product_status": product_status,
            "is_deleted": bool(is_deleted),
            "is_thumbnail": bool(is_thumbnail),
            "product_name": product_name,
            "category_ids": [int(c) for c in category_ids] if category_ids else [],
            "brand_id": int(brand_id) if brand_id is not None else None,
        }
        point = PointStruct(id=point_id, vector=vector, payload=payload)
        self.client.upsert(collection_name=self.collection_name, points=[point])

    def upsert_batch(self, points: List[PointStruct]):
        if not points:
            return
        self.client.upsert(collection_name=self.collection_name, points=points)

    def search(
        self,
        query_vector: List[float],
        limit: int = 20,
        filter_active: bool = True,
        category_id: Optional[int] = None,
        brand_id: Optional[int] = None,
        min_similarity: float = 0.65,
    ) -> List[Dict[str, Any]]:
        conditions = []
        if filter_active:
            conditions.append(FieldCondition(key="is_deleted", match=MatchValue(value=False)))
        if category_id is not None:
            conditions.append(FieldCondition(key="category_ids", match=MatchValue(value=int(category_id))))
        if brand_id is not None:
            conditions.append(FieldCondition(key="brand_id", match=MatchValue(value=int(brand_id))))

        query_filter = Filter(must=conditions) if conditions else None

        scored_points: List[ScoredPoint] = self.client.search(
            collection_name=self.collection_name,
            query_vector=query_vector,
            query_filter=query_filter,
            limit=limit * 3,  # Lay du phong de deduplicate product_id
        )

        seen_products = set()
        candidates = []
        for point in scored_points:
            score = float(point.score)
            # Nguong tuong dong Confidence Score Cutoff (loai bo ket qua rac/khong lien quan)
            if score < min_similarity:
                continue

            pid = point.payload.get("product_id")
            if pid in seen_products:
                continue
            seen_products.add(pid)
            candidates.append(
                {
                    "product_id": pid,
                    "image_id": point.payload.get("image_id"),
                    "image_url": point.payload.get("image_url"),
                    "product_name": point.payload.get("product_name"),
                    "category_ids": point.payload.get("category_ids", []),
                    "brand_id": point.payload.get("brand_id"),
                    "similarity": score,
                }
            )
            if len(candidates) == limit:
                break
        return candidates

    def delete_by_image_id(self, product_id: int, image_id: int):
        point_id = self.make_point_id(product_id, image_id)
        self.client.delete(collection_name=self.collection_name, points_selector=[point_id])

    def get_status(self) -> Dict[str, Any]:
        try:
            info = self.client.get_collection(collection_name=self.collection_name)
            points_count = info.points_count if hasattr(info, "points_count") else 0
            indexed_vectors_count = info.indexed_vectors_count if hasattr(info, "indexed_vectors_count") else 0
            return {
                "collection_name": self.collection_name,
                "status": "ready",
                "points_count": points_count or 0,
                "indexed_vectors_count": indexed_vectors_count or 0,
                "dimension": self.dimension,
            }
        except Exception as exc:
            return {
                "collection_name": self.collection_name,
                "status": "error",
                "error": str(exc),
                "points_count": 0,
            }


_store_instance = None


def get_image_store(settings) -> QdrantImageStore:
    global _store_instance
    if _store_instance is None:
        _store_instance = QdrantImageStore(
            storage_path=settings.qdrant_storage_path,
            collection_name=settings.qdrant_collection,
            dimension=settings.vector_dimension,
        )
    return _store_instance

