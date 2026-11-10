import logging
from pathlib import Path
import threading
from typing import List, Union

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
