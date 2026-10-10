import json
from pathlib import Path
from typing import Any, List, Optional

from app.core.logging import LatencyTracker, getLogger
from app.models.database import initDb
from app.repositories.product_repository import ProductRepository
from app.repositories.vector_repository import vectorRepository
from app.schemas.product import ChunkMetadata, ChunkType, ProductChunk, ProductCreate
from app.services.ingestion.cleaning import TextCleaner
from app.services.retrieval.search import bm25Service, embeddingService

logger = getLogger("ingestion_pipeline")


class ProductChunker:

    @classmethod
    def chunk(cls, product: dict[str, Any]) -> List[ProductChunk]:
        productId = str(product.get("id", ""))
        name = TextCleaner.clean(str(product.get("name", "")))
        brand = TextCleaner.clean(str(product.get("brand", "")))
        category = TextCleaner.clean(str(product.get("category", "")))
        price = float(product.get("price", 0.0))
        rating = float(product.get("rating", 5.0))
        totalReviews = int(product.get("total_reviews", product.get("totalReviews", 0)))
        totalSold = int(product.get("total_sold", product.get("totalSold", 0)))
        targetType = str(product.get("target_type", product.get("targetType", "PRODUCT"))).upper()
        durationMinutes = int(product.get("duration_minutes", product.get("durationMinutes", 0)))

        skinType = product.get("skin_type", product.get("skinType", []))
        if isinstance(skinType, str):
            skinType = [s.strip() for s in skinType.split(",") if s.strip()]

        concerns = product.get("concerns", [])
        if isinstance(concerns, str):
            concerns = [c.strip() for c in concerns.split(",") if c.strip()]

        metadataBase = {
            "product_id": productId,
            "category": category,
            "brand": brand,
            "skin_type": skinType,
            "concerns": concerns,
            "price": price,
            "rating": rating,
            "total_reviews": totalReviews,
            "total_sold": totalSold,
            "target_type": targetType,
            "duration_minutes": durationMinutes,
        }

        chunks: List[ProductChunk] = []

        descRaw = product.get("description", "")
        if targetType == "SERVICE":
            descText = (
                f"Dịch vụ Spa & Liệu trình: {name}. Thuộc danh mục: {category}. "
                f"Cơ sở thực hiện: {brand}. Thời lượng: {durationMinutes} phút. Giá: {price:,.0f} VND. "
                f"Chỉ định loại da: {', '.join(skinType) if skinType else 'Mọi loại da'}. "
                f"Vấn đề da giải quyết: {', '.join(concerns) if concerns else 'Chăm sóc toàn diện'}. "
                f"Mô tả chi tiết: {TextCleaner.clean(descRaw)}"
            )
        else:
            descText = (
                f"Sản phẩm: {name} của thương hiệu {brand}. Danh mục: {category}. "
                f"Phù hợp loại da: {', '.join(skinType)}. Vấn đề da: {', '.join(concerns)}. "
                f"Mô tả chi tiết: {TextCleaner.clean(descRaw)}"
            )
        chunks.append(
            ProductChunk(
                chunk_id=f"{productId}_desc",
                product_id=productId,
                chunk_type=ChunkType.DESCRIPTION,
                content=descText.strip(),
                metadata=ChunkMetadata(**metadataBase, chunk_type=ChunkType.DESCRIPTION),
            )
        )

        ingredientsData = product.get("ingredients", "")
        if isinstance(ingredientsData, list):
            ingContent = "; ".join([TextCleaner.clean(i) for i in ingredientsData if TextCleaner.clean(i)])
        else:
            ingContent = TextCleaner.clean(str(ingredientsData))

        if ingContent:
            ingText = (
                f"Thành phần hoạt chất của {name} ({brand}): {ingContent}. "
                f"Công dụng chính giải quyết vấn đề: {', '.join(concerns)} cho da {', '.join(skinType)}."
            )
            chunks.append(
                ProductChunk(
                    chunk_id=f"{productId}_ing",
                    product_id=productId,
                    chunk_type=ChunkType.INGREDIENTS,
                    content=ingText.strip(),
                    metadata=ChunkMetadata(**metadataBase, chunk_type=ChunkType.INGREDIENTS),
                )
            )

        benefitsData = product.get("benefits", "")
        if isinstance(benefitsData, list):
            benContent = " | ".join([TextCleaner.clean(b) for b in benefitsData if TextCleaner.clean(b)])
        else:
            benContent = TextCleaner.clean(str(benefitsData))

        if benContent:
            benText = f"Công dụng và lợi ích nổi bật của {name}: {benContent}."
            chunks.append(
                ProductChunk(
                    chunk_id=f"{productId}_ben",
                    product_id=productId,
                    chunk_type=ChunkType.BENEFITS,
                    content=benText.strip(),
                    metadata=ChunkMetadata(**metadataBase, chunk_type=ChunkType.BENEFITS),
                )
            )

        usageRaw = product.get("usage", "")
        if usageRaw:
            chunkT = ChunkType.PROCEDURE if targetType == "SERVICE" else ChunkType.USAGE
            prefix = f"Quy trình thực hiện liệu trình {name} ({durationMinutes} phút):" if targetType == "SERVICE" else f"Cách dùng và chỉ định cho {name}:"
            usageText = f"{prefix} {TextCleaner.clean(usageRaw)}."
            chunks.append(
                ProductChunk(
                    chunk_id=f"{productId}_use",
                    product_id=productId,
                    chunk_type=chunkT,
                    content=usageText.strip(),
                    metadata=ChunkMetadata(**metadataBase, chunk_type=chunkT),
                )
            )

        reviewsRaw = product.get("reviews", [])
        if reviewsRaw:
            revTexts = []
            for r in reviewsRaw:
                if isinstance(r, dict):
                    comment = r.get("comment") or r.get("review") or ""
                    star = r.get("rating", "")
                    revTexts.append(f"{star} sao: {TextCleaner.clean(str(comment))}" if star else TextCleaner.clean(str(comment)))
                elif isinstance(r, str):
                    revTexts.append(TextCleaner.clean(r))
            revContent = " | ".join([t for t in revTexts if t])
            if revContent:
                revText = f"Đánh giá từ khách hàng về {name}: {revContent}."
                chunks.append(
                    ProductChunk(
                        chunk_id=f"{productId}_rev",
                        product_id=productId,
                        chunk_type=ChunkType.REVIEWS,
                        content=revText.strip(),
                        metadata=ChunkMetadata(**metadataBase, chunk_type=ChunkType.REVIEWS),
                    )
                )

        return chunks

    chunkProduct = chunk
    chunk_product = chunk


AspectChunker = ProductChunker


class IndexingService:

    def __init__(self, productRepo: Optional[ProductRepository] = None, **kwargs):
        self.productRepo = productRepo or kwargs.get("product_repo") or ProductRepository()
        self.product_repo = self.productRepo
        initDb()

    async def indexProducts(self, products: List[dict[str, Any]]) -> dict[str, Any]:
        tracker = LatencyTracker("indexing_pipeline")
        allChunks: List[ProductChunk] = []

        with tracker.measure("sql_storage"):
            for p in products:
                prodCreate = ProductCreate(
                    id=str(p["id"]),
                    name=TextCleaner.clean(str(p.get("name", ""))),
                    brand=TextCleaner.clean(str(p.get("brand", ""))),
                    category=TextCleaner.clean(str(p.get("category", ""))),
                    price=float(p.get("price", 0.0)),
                    description=TextCleaner.clean(str(p.get("description", ""))),
                    ingredients="; ".join(p["ingredients"]) if isinstance(p.get("ingredients"), list) else str(p.get("ingredients", "")),
                    benefits=" | ".join(p["benefits"]) if isinstance(p.get("benefits"), list) else str(p.get("benefits", "")),
                    skin_type=p.get("skin_type", p.get("skinType", [])),
                    concerns=p.get("concerns", []),
                    usage=TextCleaner.clean(str(p.get("usage", ""))),
                    rating=float(p.get("rating", 5.0)),
                    total_reviews=int(p.get("total_reviews", p.get("totalReviews", 0))),
                    total_sold=int(p.get("total_sold", p.get("totalSold", 0))),
                    reviews=[f"{r.get('author', 'Khách')}: {r.get('comment', '')}" if isinstance(r, dict) else str(r) for r in p.get("reviews", [])],
                    in_stock=p.get("in_stock", True) if "in_stock" in p else (p.get("stock", 1) > 0),
                    image_url=p.get("image_url", p.get("imageUrl")),
                    target_type=p.get("target_type", p.get("targetType", "PRODUCT")),
                    duration_minutes=int(p.get("duration_minutes", p.get("durationMinutes", 0))),
                )
                self.productRepo.createOrUpdate(prodCreate)

        with tracker.measure("chunking"):
            for p in products:
                chunks = ProductChunker.chunk(p)
                allChunks.extend(chunks)

        with tracker.measure("vector_embedding"):
            textsToEmbed = [c.content for c in allChunks]
            vectors = await embeddingService.embedBatch(textsToEmbed)

        with tracker.measure("qdrant_upsert"):
            vectorsStored = vectorRepository.upsertChunks(allChunks, vectors)

        with tracker.measure("bm25_indexing"):
            bm25Service.buildIndex(allChunks)

        totalLat = tracker.getTotalDuration()

        return {
            "products_indexed": len(products),
            "chunks_created": len(allChunks),
            "vectors_stored": vectorsStored,
            "total_latency_ms": totalLat,
            "latency_breakdown": tracker.getDurations(),
        }

    index_products = indexProducts

    async def ingestCatalogFromFile(self, filePath: str | Path, **kwargs) -> dict[str, Any]:
        filePath = kwargs.get("file_path", filePath)
        p = Path(filePath)
        if not p.exists():
            raise FileNotFoundError(f"Không tìm thấy file catalog tại: {filePath}")

        with open(p, "r", encoding="utf-8") as f:
            rawData = json.load(f)

        products = rawData.get("products", rawData) if isinstance(rawData, dict) else rawData
        return await self.indexProducts(products)

    ingest_catalog_from_file = ingestCatalogFromFile


indexingService = IndexingService()
indexing_service = indexingService
