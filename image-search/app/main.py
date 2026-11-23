from asyncio import sleep
from contextlib import asynccontextmanager
import logging
import secrets
from threading import Lock
from time import monotonic
from typing import Optional

from fastapi import Depends, FastAPI, File, Header, HTTPException, Query, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from starlette.concurrency import run_in_threadpool

from app.packaging_match import matches_printed_brand, matches_printed_family, printed_brand, printed_product_family
from app.catalog import (
    decode_image_bytes,
    fetch_product_text_metadata,
    preprocess_image_square,
    read_catalog_image,
)
from app.config import get_settings
from app.model import combine_vectors, get_encoder
from app.qdrant_store import get_image_store
from app.reindex import ReindexManager
from app.vision import analyze_product_with_gemini

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")
logger = logging.getLogger("image_search_app")
settings = get_settings()

encoder = None
image_store = None
reindex_manager = None
image_work = Lock()


@asynccontextmanager
async def lifespan(app: FastAPI):
    global encoder, image_store, reindex_manager
    logger.info("Khoi tao Image Search Service tren cong %d...", settings.port)
    encoder = get_encoder(settings)
    image_store = get_image_store(settings)
    reindex_manager = ReindexManager(settings, encoder, image_store, image_work)
    yield
    logger.info("Tat Image Search Service.")


app = FastAPI(
    title="BeautyShop Image Search Engine",
    description="Dich vu tim kiem hinh anh san pham bang AI CLIP va Qdrant Vector Database rieng biet",
    version="2.0.0",
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


@asynccontextmanager
async def image_work_slot(sync: bool = False):
    if sync:
        deadline = monotonic() + 15
        while image_work.locked() and monotonic() < deadline:
            await sleep(0.1)

    if not image_work.acquire(blocking=False):
        raise HTTPException(
            status_code=503,
            detail="Dịch vụ đang xử lý ảnh khác. Vui lòng thử lại sau.",
            headers={"Retry-After": "2"},
        )
    try:
        yield
    finally:
        image_work.release()


async def reserve_image_work():
    async with image_work_slot():
        yield


async def reserve_sync_work():
    async with image_work_slot(sync=True):
        yield


class SyncImageRequest(BaseModel):
    product_id: int
    image_id: int
    image_url: str
    product_status: Optional[str] = "ACTIVE"
    is_thumbnail: Optional[bool] = False
    product_name: Optional[str] = ""
    category_ids: Optional[list[int]] = None
    brand_id: Optional[int] = None


@app.get("/health", tags=["He thong"])
async def health():
    status = image_store.get_status() if image_store else {"status": "starting"}
    return {
        "status": "UP",
        "service": "image-search",
        "qdrant_collection": settings.qdrant_collection,
        "indexed_points": status.get("points_count", 0),
        "target_detection_enabled": settings.target_detection_enabled,
        "gemini_active": bool(settings.effective_gemini_api_key),
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
        "target_detection_enabled": settings.target_detection_enabled,
        "ocr_hybrid_enabled": settings.ocr_hybrid_enabled,
        "gemini_active": bool(settings.effective_gemini_api_key),
        "reindex": reindex_stat,
    }


@app.post("/image-search/search", dependencies=[Depends(verify_token), Depends(reserve_image_work)], tags=["Tim kiem"])
async def search_by_image(
    image: UploadFile = File(...),
    limit: int = Query(default=20, ge=1, le=50),
    min_similarity: float = Query(default=0.70, ge=0.0, le=1.0),
    category_id: Optional[int] = Query(default=None),
    brand_id: Optional[int] = Query(default=None),
):
    try:
        raw_bytes = await image.read(settings.image_search_max_bytes + 1)
    finally:
        await image.close()

    if not raw_bytes:
        raise HTTPException(status_code=400, detail="Vui long chon tap tin hinh anh hop le")
    if len(raw_bytes) > settings.image_search_max_bytes:
        raise HTTPException(status_code=413, detail="Dung luong anh vuot qua gioi han 5 MiB")

    # 1. Decode anh goc de Vision nhan dien chinh xac goc nhin va bo cuc
    try:
        pil_raw = decode_image_bytes(raw_bytes, settings.image_search_max_pixels, square_pad=False)
    except Exception as exc:
        raise HTTPException(status_code=400, detail=str(exc))

    target_image = None
    extracted_text = ""
    detected_meta = {}

    # 2. Xử lý nhận diện vật thể và trích xuất bao bì qua Gemini Vision
    api_key = settings.effective_gemini_api_key
    if settings.target_detection_enabled and api_key:
        vision_res = await analyze_product_with_gemini(
            pil_raw,
            api_key=api_key,
            model=settings.gemini_model,
            timeout=settings.gemini_timeout,
        )

        if not vision_res.has_product:
            pil_raw.close()
            logger.info("Anh bi tu choi do khong chua san pham my pham: %s", vision_res.rejection_reason)
            return {
                "status": "success",
                "match_status": "EMPTY",
                "index_version": "qdrant-clip-v2-vision",
                "message": vision_res.rejection_reason or "Không nhận diện được sản phẩm mỹ phẩm cụ thể trong ảnh.",
                "items": [],
                "detected": {
                    "has_product": False,
                    "rejection_reason": vision_res.rejection_reason,
                },
            }

        # Neu co san pham va crop duoc thi dung anh crop, nguoc lai dung anh goc
        if vision_res.cropped_image:
            target_image = preprocess_image_square(vision_res.cropped_image)
            vision_res.cropped_image.close()
        else:
            target_image = preprocess_image_square(pil_raw)

        # Gom nhặt text bao bì
        tokens = []
        if vision_res.extracted_brand:
            tokens.append(vision_res.extracted_brand)
        if vision_res.extracted_product_name:
            tokens.append(vision_res.extracted_product_name)
        if vision_res.packaging_text:
            tokens.append(vision_res.packaging_text)
        extracted_text = " ".join(dict.fromkeys(tokens)).strip()

        detected_meta = {
            "has_product": True,
            "brand": vision_res.extracted_brand,
            "product_name": vision_res.extracted_product_name,
            "packaging_text": vision_res.packaging_text,
            "box_2d": vision_res.box_2d,
        }
    else:
        target_image = preprocess_image_square(pil_raw)

    pil_raw.close()

    # 3. Tạo vector ảnh
    try:
        img_vector = await run_in_threadpool(encoder.encode_single, target_image)
    finally:
        target_image.close()

    # 4. Tạo vector text và hòa trộn vector đa phương thái
    txt_vector = None
    if settings.ocr_hybrid_enabled and extracted_text:
        txt_vector = await run_in_threadpool(encoder.encode_text_single, extracted_text)

    if txt_vector:
        query_vector = combine_vectors(
            img_vector, txt_vector, image_weight=settings.hybrid_image_weight
        )
    else:
        query_vector = img_vector

    # 5. Truy vấn Qdrant Vector DB
    effective_min_sim = max(min_similarity, settings.min_similarity_threshold)
    packaging_text = detected_meta.get("packaging_text")
    brand_key = printed_brand(detected_meta.get("brand"), packaging_text)
    product_family = printed_product_family(packaging_text)
    candidates = await run_in_threadpool(
        image_store.search,
        query_vector,
        50 if brand_key or product_family else limit,
        filter_active=True,
        category_id=category_id,
        brand_id=brand_id,
        min_similarity=effective_min_sim,
    )

    if brand_key:
        candidates = [candidate for candidate in candidates if matches_printed_brand(candidate, brand_key)]
    if product_family:
        candidates = [candidate for candidate in candidates if matches_printed_family(candidate, product_family)]

    # 6. Re-ranking: Tăng nhẹ điểm tương đồng nếu chữ bao bì khớp với tên sản phẩm hoặc thương hiệu
    if candidates and (detected_meta.get("brand") or detected_meta.get("product_name")):
        brand_lower = (detected_meta.get("brand") or "").lower()
        name_lower = (detected_meta.get("product_name") or "").lower()

        for cand in candidates:
            p_name = (cand.get("product_name") or "").lower()
            boost = 0.0
            if brand_lower and brand_lower in p_name:
                boost += 0.04
            if name_lower:
                words = [w for w in name_lower.split() if len(w) >= 3]
                matched_words = sum(1 for w in words if w in p_name)
                if matched_words >= 2:
                    boost += 0.06
                elif matched_words == 1:
                    boost += 0.03

            if boost > 0:
                cand["similarity"] = min(0.99, round(cand["similarity"] + boost, 4))
                cand["re_ranked"] = True

        candidates.sort(key=lambda x: x["similarity"], reverse=True)

    return {
        "status": "success",
        "match_status": "CANDIDATES" if candidates else "EMPTY",
        "index_version": "qdrant-clip-v2-vision",
        "detected": detected_meta,
        "items": candidates[:limit],
        **({"message": "Chưa tìm thấy sản phẩm phù hợp với thông tin trên bao bì."}
           if (brand_key or product_family) and not candidates else {}),
    }


@app.post("/image-search/sync", dependencies=[Depends(verify_token), Depends(reserve_sync_work)], tags=["Dong bo"])
async def sync_single_image(req: SyncImageRequest):
    """Dong bo hoac cap nhat vector cho 1 anh don le khi admin upload/sua."""
    raw = await run_in_threadpool(
        read_catalog_image,
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
        img_vector = await run_in_threadpool(encoder.encode_single, pil_image)
    finally:
        pil_image.close()

    # Lay them text metadata (brand, product_name, volumes ml) tu MySQL de tao vector hoa tron
    meta = await run_in_threadpool(fetch_product_text_metadata, settings, req.product_id)
    brand_name = meta.get("brand_name") or ""
    product_name = meta.get("product_name") or req.product_name or ""
    volumes = meta.get("volumes") or ""
    catalog_text = meta.get("catalog_text") or " ".join([brand_name, product_name, volumes]).strip()

    if settings.ocr_hybrid_enabled and catalog_text:
        txt_vector = await run_in_threadpool(encoder.encode_text_single, catalog_text)
        vector = combine_vectors(img_vector, txt_vector, image_weight=settings.hybrid_image_weight)
    else:
        vector = img_vector

    await run_in_threadpool(
        image_store.upsert_image,
        product_id=req.product_id,
        image_id=req.image_id,
        image_url=req.image_url,
        vector=vector,
        product_status=req.product_status or "ACTIVE",
        is_deleted=False,
        is_thumbnail=bool(req.is_thumbnail),
        product_name=product_name,
        brand_name=brand_name,
        volumes=volumes,
        category_ids=req.category_ids,
        brand_id=req.brand_id,
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
