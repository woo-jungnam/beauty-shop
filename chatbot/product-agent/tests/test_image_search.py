import io
import json
from types import SimpleNamespace

import numpy as np
from PIL import Image
import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.image_search.service import (ImageSearchService, InvalidImage, UnsupportedImage,
                                      Unavailable, decode_image, publish_index)
from app.image_search.catalog import read_catalog_image
from app.api.image_search import router, authorize, ImageSearchBodyLimit


class Encoder:
    def encode(self, images, **kwargs):
        return np.array([[1., 0.]] * len(images), dtype=np.float32)

    def get_sentence_embedding_dimension(self):
        return 2


@pytest.fixture
def settings(tmp_path):
    return SimpleNamespace(
        image_search_enabled=True, image_search_model_id="test-model", image_search_model_revision="test-revision",
        image_search_index_path=str(tmp_path / "index.npz"), image_search_max_pixels=10000,
        image_search_batch_size=2, image_search_internal_token="test-only",
        image_search_max_bytes=1024, image_search_upload_dir=str(tmp_path / "uploads"),
        image_search_media_origin="http://localhost:8080", image_search_allowed_origins="",
        image_search_excluded_hosts="images.unsplash.com",
        image_search_http_timeout=1,
    )


def png(size=(4, 4), format="PNG"):
    stream = io.BytesIO()
    Image.new("RGB", size, color="red").save(stream, format=format)
    return stream.getvalue()


def indexed(settings):
    service = ImageSearchService(settings, encoder=Encoder())
    # Product 2 has two matching photos; product IDs break equal-score ties.
    publish_index(service, [[1, 0], [0.8, 0.2], [1, 0], [0, 1]], [2, 2, 1, 3], [20, 21, 10, 30], ["a"] * 4)
    return service


def test_cosine_grouping_and_stable_ties(settings):
    result = indexed(settings).search(png(), 3)
    assert [item["product_id"] for item in result["items"]] == [1, 2, 3]
    assert [item["image_id"] for item in result["items"]] == [10, 20, 30]
    assert result["items"][0]["similarity"] == pytest.approx(1)


def test_reloads_snapshot_across_service_instances(settings):
    first = indexed(settings)
    second = ImageSearchService(settings, Encoder())
    second.search(png())
    published = publish_index(first, [[1, 0]], [9], [90], ["b"])
    result = second.search(png())
    assert result["items"][0]["product_id"] == 9
    assert result["index_version"] == published["index_version"]


@pytest.mark.parametrize("kind", ["empty", "dimension", "ids", "nan"])
def test_invalid_index_not_published(settings, kind):
    service = indexed(settings)
    before = open(settings.image_search_index_path, "rb").read()
    vectors, ids, image_ids = [[1, 0]], [1], [10]
    if kind == "empty": vectors, ids, image_ids = [], [], []
    if kind == "dimension": vectors = [[1, 0, 0]]
    if kind == "ids": ids = [0]
    if kind == "nan": vectors = [[float("nan"), 0]]
    with pytest.raises(ValueError):
        publish_index(service, vectors, ids, image_ids, ["x"])
    assert open(settings.image_search_index_path, "rb").read() == before


def test_rejects_index_for_other_model_revision(settings):
    service = indexed(settings)
    service.revision = "different-model"
    with pytest.raises(ValueError):
        service.load_index()


def test_rejects_old_indexes_without_uploads_only_source_policy(settings):
    service = indexed(settings)
    with np.load(settings.image_search_index_path, allow_pickle=False) as archive:
        arrays = {name: archive[name].copy() for name in archive.files}
    metadata = json.loads(str(arrays["metadata"].item()))
    del metadata["catalog_source"]
    arrays["metadata"] = np.array(json.dumps(metadata))
    np.savez_compressed(settings.image_search_index_path, **arrays)
    with pytest.raises(ValueError): service.load_index()


def test_corrupted_published_index_fails_closed(settings):
    service = indexed(settings)
    with open(settings.image_search_index_path, "wb") as stream:
        stream.write(b"invalid")
    with pytest.raises(Unavailable): service.search(png())


def test_image_validation():
    with pytest.raises(InvalidImage): decode_image(b"broken")
    with pytest.raises(InvalidImage): decode_image(png((20, 20)), 100)
    with pytest.raises(UnsupportedImage): decode_image(png(format="BMP"))
    assert decode_image(png()).mode == "RGB"


def test_exif_orientation_is_applied():
    image = Image.new("RGB", (12, 6))
    exif = Image.Exif()
    exif[274] = 6
    raw = io.BytesIO()
    image.save(raw, format="JPEG", exif=exif)
    assert decode_image(raw.getvalue()).size == (6, 12)


def test_media_path_and_origin_restrictions(settings, tmp_path):
    root = tmp_path / "uploads"
    root.mkdir()
    (root / "photo.png").write_bytes(png())
    assert read_catalog_image(settings, "/uploads/photo.png") == png()
    with pytest.raises(ValueError): read_catalog_image(settings, "/uploads/../outside.png")
    with pytest.raises(ValueError): read_catalog_image(settings, "https://untrusted.example/a.png")
    with pytest.raises(ValueError): read_catalog_image(settings, "file:///etc/passwd")
    settings.image_search_allowed_origins = "https://images.unsplash.com"
    with pytest.raises(ValueError): read_catalog_image(settings, "https://images.unsplash.com/photo.png")


def test_only_mounted_files_are_read_even_if_remote_origin_is_allowed(settings, tmp_path):
    root = tmp_path / "uploads"
    root.mkdir()
    (root / "photo.png").write_bytes(png())
    settings.image_search_allowed_origins = "https://shop.example,https://images.unsplash.com"
    settings.image_search_excluded_hosts = ""
    assert read_catalog_image(settings, "https://shop.example/uploads/photo.png") == png()
    with pytest.raises(ValueError): read_catalog_image(settings, "https://shop.example/photos/photo.png")
    with pytest.raises(FileNotFoundError): read_catalog_image(settings, "/uploads/missing.png")
    # Unsplash stays blocked even with a local file and an explicit allowlist entry.
    with pytest.raises(ValueError): read_catalog_image(settings, "https://images.unsplash.com/uploads/photo.png")
    with pytest.raises(ValueError): read_catalog_image(settings, "https://unsplash.com/uploads/photo.png")


@pytest.fixture
def client(settings, monkeypatch):
    import app.api.image_search as api
    monkeypatch.setattr(api, "getSettings", lambda: settings)
    app = FastAPI()
    app.state.image_search = indexed(settings)
    app.include_router(router)
    app.add_middleware(ImageSearchBodyLimit, max_bytes=settings.image_search_max_bytes, token="test-only")
    with TestClient(app) as client:
        yield client


def test_api_contract_and_auth(client):
    assert client.post("/image-search/search", files={"image": ("a.png", png())}).status_code == 403
    result = client.post("/image-search/search", headers={"X-Image-Search-Token": "test-only"},
                         files={"image": ("a.png", png())})
    assert result.status_code == 200
    assert result.json()["match_status"] == "CANDIDATES"
    assert result.json()["items"][0]["product_id"] == 1


@pytest.mark.parametrize("raw,status", [(b"bad", 400), (png(format="BMP"), 415), (b"x" * 1025, 413)])
def test_api_invalid_files(client, raw, status):
    response = client.post("/image-search/search", headers={"X-Image-Search-Token": "test-only"},
                           files={"image": ("a.png", raw)})
    assert response.status_code == status


def test_api_disabled_and_status(client, settings):
    settings.image_search_enabled = False
    headers = {"X-Image-Search-Token": "test-only"}
    assert client.post("/image-search/search", headers=headers, files={"image": ("a.png", png())}).status_code == 503
    assert client.get("/image-search/status", headers=headers).json()["ready"] is False


def test_limit_before_multipart_parser(client):
    response = client.post("/image-search/search", headers={"X-Image-Search-Token": "test-only"},
                           content=b"x" * 70000)
    assert response.status_code == 413
