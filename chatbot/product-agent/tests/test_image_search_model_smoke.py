"""Opt-in real CLIP smoke; temporary synthetic fixtures, not product accuracy data."""
import os

import numpy as np
from PIL import Image
import pytest

from app.core.config import getSettings
from app.image_search.service import ImageSearchService, publish_index


@pytest.mark.skipif(os.environ.get("RUN_IMAGE_MODEL_SMOKE") != "1", reason="Opt-in model download/CPU inference")
def test_real_clip_encodes_and_retrieves_temporary_index(tmp_path):
    settings = getSettings().model_copy(update={
        "image_search_enabled": True,
        "image_search_index_path": str(tmp_path / "index.npz"),
    })
    service = ImageSearchService(settings)
    service.load_model()
    images = [Image.new("RGB", (224, 224), color=color) for color in ("red", "blue")]
    vectors = service.encode_images(images)
    assert vectors.shape == (2, 512)
    assert np.isfinite(vectors).all()
    np.testing.assert_allclose(np.linalg.norm(vectors, axis=1), 1, atol=1e-5)
    publish_index(service, vectors, [1, 2], [11, 22], ["fixture-red", "fixture-blue"])
    query = tmp_path / "query.png"
    images[0].save(query)
    result = service.search(query.read_bytes(), 2)
    assert result["items"][0]["product_id"] == 1
    assert result["match_status"] == "CANDIDATES"
    for image in images: image.close()
