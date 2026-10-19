"""CLIP image retrieval, with immutable validated snapshots and bounded inference."""
from dataclasses import dataclass
from datetime import datetime, timezone
from io import BytesIO
import json
import os
from pathlib import Path
import threading
import uuid
import warnings

import numpy as np
from PIL import Image, ImageOps, UnidentifiedImageError

PREPROCESSING = "pillow-exif-rgb-v1"
CATALOG_SOURCE = "uploads-only-v1"
CATALOG_FORMATS = {"JPEG", "PNG", "WEBP", "AVIF", "GIF", "BMP"}


class InvalidImage(ValueError):
    pass


class UnsupportedImage(InvalidImage):
    pass


class Unavailable(RuntimeError):
    pass


class Busy(Unavailable):
    pass


def decode_image(raw, max_pixels=20_000_000, allowed_formats=None):
    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error", Image.DecompressionBombWarning)
            with Image.open(BytesIO(raw)) as image:
                if image.format not in (allowed_formats or {"JPEG", "PNG", "WEBP"}):
                    raise UnsupportedImage("Chỉ hỗ trợ JPEG, PNG hoặc WebP.")
                if image.width * image.height > max_pixels:
                    raise InvalidImage("Ảnh có độ phân giải quá lớn.")
                image.load()
                return ImageOps.exif_transpose(image).convert("RGB")
    except InvalidImage:
        raise
    except (UnidentifiedImageError, OSError, ValueError, Image.DecompressionBombError,
            Image.DecompressionBombWarning) as exc:
        raise InvalidImage("Ảnh trống, hỏng hoặc có độ phân giải quá lớn.") from exc


def normalize(vectors):
    vectors = np.asarray(vectors, dtype=np.float32)
    if vectors.ndim != 2 or len(vectors) == 0 or not np.isfinite(vectors).all():
        raise ValueError("Invalid vectors")
    norms = np.linalg.norm(vectors, axis=1, keepdims=True)
    if not np.isfinite(norms).all() or np.any(norms <= 1e-12):
        raise ValueError("Zero/invalid norm")
    return vectors / norms


@dataclass(frozen=True)
class Snapshot:
    vectors: np.ndarray
    product_ids: np.ndarray
    image_ids: np.ndarray
    metadata: dict


class ImageSearchService:
    def __init__(self, settings, encoder=None):
        self.settings = settings
        self.encoder = encoder
        self.snapshot = None
        self.error = None
        self._stamp = None
        self._reload_lock = threading.Lock()
        self._inference = threading.Lock()
        self.revision = settings.image_search_model_revision

    def load_model(self):
        if self.encoder is None:
            from sentence_transformers import SentenceTransformer
            self.encoder = SentenceTransformer(
                self.settings.image_search_model_id,
                revision=self.settings.image_search_model_revision or None,
                cache_folder=self.settings.image_search_cache_dir,
                device="cpu",
                processor_kwargs={"use_fast": False},
            )
            # Persist the resolved model artifact rather than only a mutable branch name.
            first = self.encoder[0]
            config = getattr(getattr(first, "model", None), "config", None)
            self.revision = getattr(config, "_commit_hash", None) or self.revision
            if not self.revision:
                raise ValueError("Set IMAGE_SEARCH_MODEL_REVISION to an immutable model revision")

    def encode_images(self, images):
        if self.encoder is None:
            raise Unavailable("Mô hình ảnh chưa sẵn sàng.")
        return normalize(self.encoder.encode(
            images, batch_size=self.settings.image_search_batch_size,
            convert_to_numpy=True, normalize_embeddings=True, show_progress_bar=False,
        ))

    def load_index(self, path=None):
        with np.load(path or self.settings.image_search_index_path, allow_pickle=False) as archive:
            vectors = normalize(archive["vectors"])
            ids = archive["product_ids"]
            image_ids = archive["image_ids"]
            metadata = json.loads(str(archive["metadata"].item()))
        if (ids.ndim != 1 or image_ids.ndim != 1 or len(ids) != len(vectors)
                or len(image_ids) != len(ids) or ids.dtype.kind not in "iu"
                or image_ids.dtype.kind not in "iu" or np.any(ids <= 0)
                or np.any(image_ids == 0) or len(set(image_ids.tolist())) != len(image_ids)):
            raise ValueError("Invalid image metadata")
        if (metadata.get("model_id") != self.settings.image_search_model_id
                or metadata.get("revision") != self.revision
                or metadata.get("preprocessing") != PREPROCESSING
                or metadata.get("catalog_source") != CATALOG_SOURCE
                or not metadata.get("index_version") or not metadata.get("built_at")):
            raise ValueError("Incompatible model/index version; rebuild required")
        dimension_method = getattr(self.encoder, "get_embedding_dimension", None)
        dimension = dimension_method() if dimension_method else self.encoder.get_sentence_embedding_dimension()
        if vectors.shape[1] != dimension:
            raise ValueError("Incompatible embedding dimension")
        for array in (vectors, ids, image_ids):
            array.setflags(write=False)
        snapshot = Snapshot(vectors, ids, image_ids, metadata)
        self.snapshot = snapshot
        self.error = None
        return snapshot

    def reload_if_changed(self):
        with self._reload_lock:
            try:
                stat = Path(self.settings.image_search_index_path).stat()
                stamp = (stat.st_mtime_ns, stat.st_size, stat.st_ino)
                if stamp != self._stamp or self.snapshot is None:
                    self.load_index()
                    self._stamp = stamp
            except Exception:
                # Fail closed if the published index is unavailable or incompatible.
                self.snapshot = None
                self.error = "Chỉ mục ảnh chưa có hoặc không tương thích; cần tạo lại chỉ mục."
                raise Unavailable(self.error) from None

    def initialize(self):
        if not self.settings.image_search_enabled:
            return
        try:
            if not self.settings.image_search_internal_token:
                raise ValueError("Missing internal token")
            self.load_model()
            self.reload_if_changed()
        except Exception:
            self.error = "Không thể nạp mô hình/chỉ mục ảnh. Kiểm tra cấu hình và tạo chỉ mục."

    def status(self):
        if self.encoder is not None and self.settings.image_search_enabled:
            try:
                self.reload_if_changed()
            except Unavailable:
                pass
        snap = self.snapshot
        return {"enabled": self.settings.image_search_enabled,
                "ready": bool(self.settings.image_search_enabled and snap is not None and self.encoder is not None),
                "model_id": self.settings.image_search_model_id,
                "index_version": snap.metadata["index_version"] if snap else None,
                "images": len(snap.product_ids) if snap else 0,
                "products": len(set(snap.product_ids.tolist())) if snap else 0,
                "error": self.error}

    def search(self, raw, limit=20):
        if not 1 <= limit <= 50:
            raise ValueError("Invalid limit")
        if not self.settings.image_search_enabled or self.encoder is None:
            raise Unavailable("Tìm kiếm bằng ảnh chưa sẵn sàng.")
        # Reject excess work instead of queuing arbitrary images in memory.
        if not self._inference.acquire(blocking=False):
            raise Busy("Hệ thống tìm ảnh đang bận, vui lòng thử lại.")
        try:
            self.reload_if_changed()
            snap = self.snapshot
            image = decode_image(raw, self.settings.image_search_max_pixels)
            try:
                query = self.encode_images([image])[0]
            finally:
                image.close()
            scores = snap.vectors @ query
            order = np.lexsort((snap.image_ids, snap.product_ids, -scores))
            seen, items = set(), []
            for row in order:
                pid = int(snap.product_ids[row])
                if pid in seen:
                    continue
                seen.add(pid)
                items.append({"product_id": pid, "image_id": int(snap.image_ids[row]),
                              "similarity": float(np.clip(scores[row], -1, 1))})
                if len(items) == limit:
                    break
            return {"status": "success", "match_status": "CANDIDATES",
                    "index_version": snap.metadata["index_version"], "items": items}
        finally:
            self._inference.release()


def publish_index(service, vectors, product_ids, image_ids, fingerprints):
    target = Path(service.settings.image_search_index_path)
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_name(f"{target.stem}.{uuid.uuid4().hex}.tmp.npz")
    metadata = {"model_id": service.settings.image_search_model_id,
                "catalog_source": CATALOG_SOURCE,
                "revision": service.revision, "preprocessing": PREPROCESSING,
                "built_at": datetime.now(timezone.utc).isoformat(),
                "index_version": uuid.uuid4().hex}
    try:
        np.savez_compressed(temporary, vectors=normalize(vectors),
                            product_ids=np.asarray(product_ids, dtype=np.int64),
                            image_ids=np.asarray(image_ids, dtype=np.int64),
                            fingerprints=np.asarray(fingerprints, dtype=str),
                            metadata=np.array(json.dumps(metadata)))
        service.load_index(temporary)
        os.replace(temporary, target)
    finally:
        temporary.unlink(missing_ok=True)
    return metadata
