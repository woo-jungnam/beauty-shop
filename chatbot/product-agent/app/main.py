import re
from contextlib import asynccontextmanager
from pathlib import Path
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles

try:
    from uvicorn.protocols.http import h11_impl

    origDataReceived = h11_impl.H11Protocol.data_received

    def safeDataReceived(self, data: bytes) -> None:
        if b"h2c" in data:
            data = re.sub(rb"(?i)Upgrade:\s*h2c\r\n", rb"", data)
            data = re.sub(rb"(?i)HTTP2-Settings:[^\r\n]*\r\n", rb"", data)
            data = re.sub(rb"(?i)Connection:\s*Upgrade[^\r\n]*\r\n", rb"Connection: keep-alive\r\n", data)
        origDataReceived(self, data)

    _safe_data_received = safeDataReceived
    h11_impl.H11Protocol.data_received = safeDataReceived
except Exception:
    pass

from app.api.chat import router as apiRouter
from app.api.chat import router as api_router
from app.api.recommend import router as recommendRouter
from app.core.config import getSettings
from app.core.logging import getLogger
from app.llm.client import llmClient
from app.models.database import initDb
from app.services import bm25Service, dbExtractor, embeddingService, indexingService

settings = getSettings()
logger = getLogger("appMain")
staticDir = Path(__file__).resolve().parent / "static"
static_dir = staticDir


@asynccontextmanager
async def lifespan(app: FastAPI):
    initDb()
    if not bm25Service.chunks:
        synced = False
        try:
            logger.info("Tự động nạp dữ liệu sản phẩm từ MySQL vào kho Qdrant & chỉ mục RAG...")
            res = await dbExtractor.syncMysqlToRag()
            if settings.environment.lower() == "production" and not res["products_indexed"]:
                raise RuntimeError("The MySQL catalog is empty")
            logger.info(
                "Đã tải thành công %d sản phẩm (%d chunks) từ MySQL vào chỉ mục RAG.",
                res["products_indexed"],
                res["chunks_created"],
            )
            synced = True
        except Exception as exc:
            if settings.environment.lower() == "production":
                raise RuntimeError("Cannot start chatbot without synchronizing the MySQL catalog") from exc
            logger.warning("Thông báo đồng bộ MySQL khi khởi động: %s. Chuyển sang nạp dữ liệu mẫu.", exc)

        if not synced:
            catalogPath = Path(__file__).resolve().parent / "data" / "sample_cosmetics.json"
            if catalogPath.exists():
                try:
                    await indexingService.ingestCatalogFromFile(catalogPath)
                except Exception as exc:
                    logger.warning("Lỗi nạp danh mục sản phẩm mẫu khi khởi động: %s", exc)
    yield
    await embeddingService.aclose()
    await llmClient.aclose()


app = FastAPI(
    title=settings.appName,
    description="Hệ thống trợ lý tư vấn mỹ phẩm & làm đẹp thông minh ứng dụng kiến trúc RAG đa tầng",
    version="1.0.0",
    debug=settings.debug,
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

if staticDir.exists():
    app.mount("/static", StaticFiles(directory=str(staticDir)), name="static")

app.include_router(apiRouter)
app.include_router(recommendRouter)


@app.get("/", tags=["Giao diện"], summary="Trang giao diện tư vấn mỹ phẩm")
async def indexPage():
    indexFile = staticDir / "index.html"
    if indexFile.exists():
        return FileResponse(str(indexFile))
    return {"message": "Cosmetic AI Assistant API Sẵn sàng hoạt động"}


index_page = indexPage


@app.get("/health", tags=["Hệ thống"], summary="Kiểm tra hệ thống")
async def healthCheck() -> dict[str, object]:
    return {
        "status": "ok",
        "indexed_chunks": len(bm25Service.chunks),
        "embedding_provider": embeddingService.activeProvider,
        "embedding_fallback": embeddingService.isFallbackActive,
    }


health_check = healthCheck


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "app.main:app",
        host=settings.host,
        port=settings.port,
        reload=settings.debug,
    )
