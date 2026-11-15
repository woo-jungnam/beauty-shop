import logging
from pathlib import Path
import threading
from typing import List, Optional, Union

import numpy as np
from PIL import Image

logger = logging.getLogger("clip_encoder")


def normalize_vectors(vectors: np.ndarray) -> np.ndarray:
    vectors = np.asarray(vectors, dtype=np.float32)
    if vectors.ndim != 2 or len(vectors) == 0:
        raise ValueError("Invalid vector array")
    norms = np.linalg.norm(vectors, axis=1, keepdims=True)
    if not np.isfinite(norms).all() or np.any(norms <= 1e-12):
        raise ValueError("Zero or invalid vector norm")
    return vectors / norms


def combine_vectors(
    image_vector: List[float],
    text_vector: Optional[List[float]],
    image_weight: float = 0.70,
) -> List[float]:
    """
    Hoa tron hai vector da phuong thai (anh va chu bao bi) theo trong so
    sau do chuan hoa L2 ve do dai bang 1 cho phep tinh Cosine Distance.
    """
    if not text_vector:
        return image_vector
    try:
        img_arr = np.asarray(image_vector, dtype=np.float32)
        txt_arr = np.asarray(text_vector, dtype=np.float32)
        weight_img = max(0.0, min(1.0, float(image_weight)))
        weight_txt = 1.0 - weight_img
        combined = weight_img * img_arr + weight_txt * txt_arr
        norm = np.linalg.norm(combined)
        if norm <= 1e-12 or not np.isfinite(norm):
            return image_vector
        return (combined / norm).tolist()
    except Exception as exc:
        logger.warning("Loi khi hoa tron vector anh va chu: %s, dung vector anh goc", exc)
        return image_vector


class ClipEncoder:
    def __init__(self, model_id: str, revision: str = None, cache_dir: str = None):
        self.model_id = model_id
        self.revision = revision
        self.cache_dir = cache_dir
        self.model = None
        self._lock = threading.Lock()

    def load_model(self):
        if self.model is None:
            with self._lock:
                if self.model is None:
                    from sentence_transformers import SentenceTransformer

                    # Kiem tra neu da co ban offline snapshot trong cache de load truc tiep cuc nhanh
                    target_model = self.model_id
                    if self.cache_dir:
                        snapshot_path = (
                            Path(self.cache_dir)
                            / "models--sentence-transformers--clip-ViT-B-32"
                            / "snapshots"
                            / "327ab6726d33c0e22f920c83f2ff9e4bd38ca37f"
                        )
                        if snapshot_path.exists():
                            target_model = str(snapshot_path)

                    logger.info("Dang tai mo hinh CLIP tu: %s", target_model)
                    self.model = SentenceTransformer(
                        target_model,
                        cache_folder=self.cache_dir,
                        device="cpu",
                    )

                    # Dam bao CLIP tokenizer luon truncate ve dung 77 tokens toi da tranh loi kich thuoc tensor
                    try:
                        clip_module = self.model[0]
                        if hasattr(clip_module, "processor") and hasattr(clip_module.processor, "tokenizer"):
                            def safe_tokenize(texts, padding=True):
                                from PIL import Image as PILImage
                                images = []
                                texts_values = []
                                image_text_info = []

                                for data in texts:
                                    if isinstance(data, PILImage.Image):
                                        images.append(data)
                                        image_text_info.append(0)
                                    else:
                                        texts_values.append(str(data))
                                        image_text_info.append(1)

                                encoding = {}
                                if len(texts_values):
                                    encoding = clip_module.processor.tokenizer(
                                        texts_values,
                                        return_tensors="pt",
                                        padding=padding,
                                        truncation=True,
                                        max_length=77,
                                    )

                                if len(images):
                                    image_features = clip_module.processor.image_processor(images, return_tensors="pt")
                                    encoding["pixel_values"] = image_features.pixel_values

                                encoding["image_text_info"] = image_text_info
                                return dict(encoding)

                            clip_module.tokenize = safe_tokenize
                    except Exception as patch_err:
                        logger.warning("Khong the patch CLIP tokenizer truncation: %s", patch_err)

                    logger.info("Mo hinh CLIP da san sang hoat dong")

    def encode(self, images: List[Image.Image], batch_size: int = 16) -> np.ndarray:
        self.load_model()
        vectors = self.model.encode(
            images,
            batch_size=batch_size,
            convert_to_numpy=True,
            normalize_embeddings=True,
            show_progress_bar=False,
        )
        return normalize_vectors(vectors)

    def encode_single(self, image: Image.Image) -> List[float]:
        vectors = self.encode([image], batch_size=1)
        return vectors[0].tolist()

    def encode_text(self, texts: List[str]) -> np.ndarray:
        """Ma hoa van ban bao bi thanh vector 512D cung khong gian voi anh."""
        self.load_model()
        vectors = self.model.encode(
            texts,
            convert_to_numpy=True,
            normalize_embeddings=True,
            show_progress_bar=False,
        )
        return normalize_vectors(vectors)

    def encode_text_single(self, text: str) -> Optional[List[float]]:
        if not text or not text.strip():
            return None
        vectors = self.encode_text([text.strip()])
        return vectors[0].tolist()


_encoder_instance = None


def get_encoder(settings) -> ClipEncoder:
    global _encoder_instance
    if _encoder_instance is None:
        _encoder_instance = ClipEncoder(
            model_id=settings.image_search_model_id,
            revision=settings.image_search_model_revision,
            cache_dir=settings.image_search_cache_dir,
        )
    return _encoder_instance
