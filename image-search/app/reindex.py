from datetime import datetime, timezone
import logging
import threading
import time
from typing import Any, Dict, Optional

from qdrant_client.models import PointStruct

from app.catalog import (
    ALLOWED_FORMATS,
    decode_image_bytes,
    fetch_all_catalog_images,
    read_catalog_image,
)

logger = logging.getLogger("reindex_manager")


class ReindexManager:
    def __init__(self, settings, encoder, store):
        self.settings = settings
        self.encoder = encoder
        self.store = store

        self._lock = threading.Lock()
        self._thread: Optional[threading.Thread] = None

        self.status = "IDLE"  # IDLE, IN_PROGRESS, COMPLETED, FAILED
        self.total_images = 0
        self.processed_images = 0
        self.failed_images = 0
        self.progress_percent = 0.0
        self.start_time: Optional[float] = None
        self.elapsed_seconds = 0.0
        self.error_message: Optional[str] = None
        self.last_completed_at: Optional[str] = None

    def get_status(self) -> Dict[str, Any]:
        with self._lock:
            elapsed = 0.0
            if self.start_time:
                if self.status == "IN_PROGRESS":
                    elapsed = round(time.perf_counter() - self.start_time, 1)
                else:
                    elapsed = self.elapsed_seconds

            percent = 0.0
            if self.total_images > 0:
                percent = round(min(100.0, (self.processed_images / self.total_images) * 100), 1)

            return {
                "status": self.status,
                "total_images": self.total_images,
                "processed_images": self.processed_images,
                "failed_images": self.failed_images,
                "progress_percent": percent,
                "elapsed_seconds": elapsed,
                "error_message": self.error_message,
                "last_completed_at": self.last_completed_at,
            }

    def start_reindex(self) -> Dict[str, Any]:
        with self._lock:
            if self.status == "IN_PROGRESS":
                return {"message": "Tien trinh dong bo dang chay", "status": self.get_status()}

            self.status = "IN_PROGRESS"
            self.total_images = 0
            self.processed_images = 0
            self.failed_images = 0
            self.progress_percent = 0.0
            self.start_time = time.perf_counter()
            self.elapsed_seconds = 0.0
            self.error_message = None

            self._thread = threading.Thread(target=self._run_worker, daemon=True)
            self._thread.start()

        return {"message": "Da khoi dong tien trinh tao embedding toan bo", "status": self.get_status()}

    def _run_worker(self):
        try:
            logger.info("Bat dau quet MySQL database de lay danh sach anh catalog...")
            rows = fetch_all_catalog_images(self.settings)
            with self._lock:
                self.total_images = len(rows)

            if not rows:
                with self._lock:
                    self.status = "COMPLETED"
                    self.elapsed_seconds = round(time.perf_counter() - self.start_time, 1)
                    self.last_completed_at = datetime.now(timezone.utc).isoformat()
                logger.info("Khong co anh nao trong database can danh chi muc.")
                return

            self.encoder.load_model()

            batch_images = []
            batch_meta = []
            batch_size = self.settings.image_search_batch_size

            for row in rows:
                raw = read_catalog_image(
                    upload_dir=self.settings.image_search_upload_dir,
                    url=row["image_url"],
                    max_bytes=self.settings.image_search_max_bytes,
                )
                if not raw:
                    with self._lock:
                        self.failed_images += 1
                        self.processed_images += 1
                    continue

                try:
                    pil_img = decode_image_bytes(raw, self.settings.image_search_max_pixels)
                except Exception as exc:
                    logger.warning("Khong decode duoc anh san pham %s: %s", row.get("product_id"), exc)
                    with self._lock:
                        self.failed_images += 1
                        self.processed_images += 1
                    continue

                batch_images.append(pil_img)
                batch_meta.append(row)

                if len(batch_images) >= batch_size:
                    self._flush_batch(batch_images, batch_meta)
                    batch_images = []
                    batch_meta = []

            # Xu ly not phan du con lai
            if batch_images:
                self._flush_batch(batch_images, batch_meta)

            with self._lock:
                self.status = "COMPLETED"
                self.elapsed_seconds = round(time.perf_counter() - self.start_time, 1)
                self.last_completed_at = datetime.now(timezone.utc).isoformat()
                self.progress_percent = 100.0

            logger.info(
                "Hoan tat embedding toan bo anh! Tong: %d, Thanh cong: %d, Loi: %d, Thoi gian: %.1fs",
                self.total_images,
                self.processed_images - self.failed_images,
                self.failed_images,
                self.elapsed_seconds,
            )

        except Exception as exc:
            logger.error("Loi trong qua trinh dong bo toan bo anh: %s", exc, exc_info=True)
            with self._lock:
                self.status = "FAILED"
                self.error_message = str(exc)
                if self.start_time:
                    self.elapsed_seconds = round(time.perf_counter() - self.start_time, 1)

    def _flush_batch(self, images, meta_list):
        try:
            vectors = self.encoder.encode(images, batch_size=len(images))
            points = []
            for vec, meta in zip(vectors, meta_list):
                pid = meta["product_id"]
                img_id = meta["image_id"]
                point_id = self.store.make_point_id(pid, img_id)
                payload = {
                    "product_id": int(pid),
                    "image_id": int(img_id),
                    "image_url": meta["image_url"],
                    "product_status": meta.get("product_status", "ACTIVE"),
                    "is_deleted": False,
                    "is_thumbnail": bool(meta.get("is_thumbnail", 0)),
                    "product_name": meta.get("product_name", ""),
                }
                points.append(PointStruct(id=point_id, vector=vec.tolist(), payload=payload))

            self.store.upsert_batch(points)
            with self._lock:
                self.processed_images += len(images)
        finally:
            for img in images:
                try:
                    img.close()
                except Exception:
                    pass

