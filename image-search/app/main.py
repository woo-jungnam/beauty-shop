from contextlib import asynccontextmanager
import logging
import secrets
from typing import Optional

from fastapi import Depends, FastAPI, File, Header, HTTPException, Query, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from starlette.concurrency import run_in_threadpool

from app.catalog import decode_image_bytes, read_catalog_image
from app.config import get_settings
from app.model import get_encoder
from app.qdrant_store import get_image_store
from app.reindex import ReindexManager

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")
logger = logging.getLogger("image_search_app")
settings = get_settings()

encoder = None
image_store = None
reindex_manager = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global encoder, image_store, reindex_manager
    logger.info("Khoi tao Image Search Service tren cong %d...", settings.port)
    encoder = get_encoder(settings)
    image_store = get_image_store(settings)
    reindex_manager = ReindexManager(settings, encoder, image_store)
    yield
    logger.info("Tat Image Search Service.")


app = FastAPI(
    title="BeautyShop Image Search Engine",
    description="Dich vu tim kiem hinh anh san pham bang AI CLIP va Qdrant Vector Database rieng biet",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)


def verify_token(x_image_search_token: str = Header(default="")):
    expected = settings.image_search_internal_token
    if expected and not secrets.compare_digest(x_image_search_token, expected):
        raise HTTPException(status_code=403, detail="Khong co quyen truy cap he thong tim kiem anh")


class SyncImageRequest(BaseModel):
    product_id: int
    image_id: int
    image_url: str
    product_status: Optional[str] = "ACTIVE"
    is_thumbnail: Optional[bool] = False
    product_name: Optional[str] = ""


@app.get("/health", tags=["He thong"])
async def health():
    status = image_store.get_status() if image_store else {"status": "starting"}
    return {
        "status": "UP",
        "service": "image-search",
        "qdrant_collection": settings.qdrant_collection,
        "indexed_points": status.get("points_count", 0),
    }


@app.get("/image-search/status", dependencies=[Depends(verify_token)], tags=["Chi muc"])
async def get_index_status():
    status = image_store.get_status()
    reindex_stat = reindex_manager.get_status()
    return {
        "enabled": True,
        "ready": True,
        "model_id": settings.image_search_model_id,
        "collection_name": settings.qdrant_collection,
        "dimension": settings.vector_dimension,
        "total_vectors": status.get("points_count", 0),
        "reindex": reindex_stat,
    }


@app.post("/image-search/search", dependencies=[Depends(verify_token)], tags=["Tim kiem"])
async def search_by_image(
    image: UploadFile = File(...),
    limit: int = Query(default=20, ge=1, le=50),
):
    try:
        raw_bytes = await image.read(settings.image_search_max_bytes + 1)
    finally:
        await image.close()

    if not raw_bytes:
        raise HTTPException(status_code=400, detail="Vui long chon tap tin hinh anh hop le")
    if len(raw_bytes) > settings.image_search_max_bytes:
        raise HTTPException(status_code=413, detail="Dung luong anh vuot qua gioi han 5 MiB")

    try:
        pil_image = decode_image_bytes(raw_bytes, settings.image_search_max_pixels)
    except Exception as exc:
        raise HTTPException(status_code=400, detail=str(exc))

    try:
        query_vector = await run_in_threadpool(encoder.encode_single, pil_image)
    finally:
        pil_image.close()

    candidates = await run_in_threadpool(image_store.search, query_vector, limit)

    return {
        "status": "success",
        "match_status": "CANDIDATES" if candidates else "EMPTY",
        "index_version": "qdrant-clip-v1",
        "items": candidates,
    }


@app.post("/image-search/sync", dependencies=[Depends(verify_token)], tags=["Dong bo"])
async def sync_single_image(req: SyncImageRequest):
    """Dong bo hoac cap nhat vector cho 1 anh don le khi admin upload/sua."""
    raw = read_catalog_image(
        upload_dir=settings.image_search_upload_dir,
        url=req.image_url,
        max_bytes=settings.image_search_max_bytes,
    )
    if not raw:
        raise HTTPException(
            status_code=404,
            detail=f"Khong tim thay tep anh vat ly trong volume uploads cho URL: {req.image_url}",
        )

    try:
        pil_image = decode_image_bytes(raw, settings.image_search_max_pixels)
    except Exception as exc:
        raise HTTPException(status_code=400, detail=f"Khong the doc tep anh: {exc}")

    try:
        vector = await run_in_threadpool(encoder.encode_single, pil_image)
    finally:
        pil_image.close()

    await run_in_threadpool(
        image_store.upsert_image,
        product_id=req.product_id,
        image_id=req.image_id,
        image_url=req.image_url,
        vector=vector,
        product_status=req.product_status or "ACTIVE",
        is_deleted=False,
        is_thumbnail=bool(req.is_thumbnail),
        product_name=req.product_name or "",
    )

    return {
        "status": "success",
        "message": f"Da nạp vector thanh cong cho san pham {req.product_id}, anh {req.image_id}",
        "product_id": req.product_id,
        "image_id": req.image_id,
    }


@app.delete("/image-search/sync/{product_id}/{image_id}", dependencies=[Depends(verify_token)], tags=["Dong bo"])
async def delete_single_image(product_id: int, image_id: int):
    """Xoa point vector cua anh khoi Qdrant khi admin xoa anh."""
    await run_in_threadpool(image_store.delete_by_image_id, product_id, image_id)
    return {
        "status": "success",
        "message": f"Da xoa vector cua anh {image_id} khoi Qdrant",
    }


@app.post("/image-search/admin/reindex-all", dependencies=[Depends(verify_token)], tags=["Quan tri"])
async def trigger_reindex_all():
    """Kich hoat tien trinh doc toan bo MySQL catalog va nap lai toan bo vao Qdrant."""
    result = reindex_manager.start_reindex()
    return result


@app.get("/image-search/admin/reindex-status", dependencies=[Depends(verify_token)], tags=["Quan tri"])
async def get_reindex_progress():
    """Tra ve tien trinh dong bo toan bo realtime cho giao dien Admin."""
    return reindex_manager.get_status()

