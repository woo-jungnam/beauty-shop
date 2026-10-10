"""Regression checks for Docker ingestion and embedding recovery (no live API calls)."""
import asyncio
import json

import httpx
import pytest

from app.services.retrieval.search import EmbeddingService, settings
from app.core.config import Settings
from app.llm.client import LLMClient, LLMClientError


def make_service(monkeypatch, tmp_path, handler):
    monkeypatch.setattr(settings, "embedding_provider", "gemini")
    monkeypatch.setattr(settings, "embedding_cache_dir", str(tmp_path))
    monkeypatch.setattr(settings, "llm_api_key", "test-secret")
    monkeypatch.setattr(settings, "llm_max_retries", 0)
    service = EmbeddingService(dimension=3)
    service._client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    return service


def test_batch_embedding_preserves_order_deduplicates_and_reuses_cache(monkeypatch, tmp_path):
    requests = []

    def handler(request):
        requests.append(request)
        assert request.url.path.endswith(":batchEmbedContents")
        assert request.url.query == b""
        assert request.headers["x-goog-api-key"] == "test-secret"
        batch = json.loads(request.content)["requests"]
        assert [item["content"]["parts"][0]["text"] for item in batch] == ["serum", "toner"]
        assert all(item["outputDimensionality"] == 3 for item in batch)
        return httpx.Response(200, json={"embeddings": [{"values": [1, 0, 0]}, {"values": [0, 1, 0]}]})

    service = make_service(monkeypatch, tmp_path, handler)

    async def run():
        expected = [[1, 0, 0], [0, 1, 0], [1, 0, 0]]
        assert await service.embedBatch(["serum", "toner", "serum"]) == expected
        assert await service.embedBatch(["serum", "toner", "serum"]) == expected
        assert len(requests) == 1
        assert service._cacheFile.parent == tmp_path
        assert service._cacheFile.exists()
        assert not service.isFallbackActive
        await service.aclose()

    asyncio.run(run())


def test_temporary_embedding_failure_does_not_poison_cache(monkeypatch, tmp_path):
    calls = []

    def handler(request):
        calls.append(request)
        if len(calls) == 1:
            return httpx.Response(503, json={"error": {"message": "temporary outage"}})
        return httpx.Response(200, json={"embedding": {"values": [0, 0, 1]}})

    service = make_service(monkeypatch, tmp_path, handler)

    async def run():
        assert len(await service.embedText("serum")) == 3
        assert service.isFallbackActive
        assert "serum" not in service._cache
        assert await service.embedText("serum") == [0, 0, 1]
        assert not service.isFallbackActive
        assert len(calls) == 2
        assert all(request.url.query == b"" for request in calls)
        await service.aclose()

    asyncio.run(run())


def test_invalid_batch_vectors_are_not_cached(monkeypatch, tmp_path):
    def handler(request):
        if request.url.path.endswith(":batchEmbedContents"):
            return httpx.Response(200, json={"embeddings": [{"values": [1]}]})
        return httpx.Response(200, json={"embedding": {"values": [0, 1, 0]}})

    service = make_service(monkeypatch, tmp_path, handler)

    async def run():
        assert await service.embedBatch(["serum"]) == [[0, 1, 0]]
        assert service._cache["serum"] == [0, 1, 0]
        assert not service.isFallbackActive
        await service.aclose()

    asyncio.run(run())


def test_production_startup_rejects_unavailable_catalog(monkeypatch):
    from unittest.mock import AsyncMock
    from app import main

    monkeypatch.setattr(main.settings, "environment", "production")
    monkeypatch.setattr(main.bm25Service, "chunks", [])
    sync = AsyncMock(side_effect=ConnectionError("MySQL unavailable"))
    sample = AsyncMock()
    monkeypatch.setattr(main.dbExtractor, "syncMysqlToRag", sync)
    monkeypatch.setattr(main.indexingService, "ingestCatalogFromFile", sample)

    async def run():
        with pytest.raises(RuntimeError, match="synchronizing the MySQL catalog"):
            async with main.lifespan(main.app):
                pass

    asyncio.run(run())
    sync.assert_awaited_once()
    sample.assert_not_awaited()


def make_llm(handler, retries=1):
    config = Settings(
        _env_file=None,
        llm_api_key="test-secret",
        llm_base_url="https://example.invalid",
        llm_model="primary",
        llm_fallback_model="backup",
        llm_max_retries=retries,
        llm_retry_delay=0,
    )
    return LLMClient(config, httpClient=httpx.AsyncClient(transport=httpx.MockTransport(handler)))


def stream_response():
    return httpx.Response(200, headers={"content-type": "text/event-stream"}, text=(
        'data: {"choices":[{"delta":{"content":"Hello"}}]}\n\n'
        'data: [DONE]\n\n'
    ))


def test_stream_retries_temporary_provider_error(monkeypatch):
    from unittest.mock import AsyncMock
    sleep = AsyncMock()
    monkeypatch.setattr(asyncio, "sleep", sleep)
    models = []

    def handler(request):
        models.append(json.loads(request.content)["model"])
        return httpx.Response(503, json={"error": {"message": "temporary outage"}}) if len(models) == 1 else stream_response()

    llm = make_llm(handler)

    async def run():
        assert [token async for token in llm.streamText("hello")] == ["Hello"]
        assert models == ["primary", "primary"]
        sleep.assert_awaited_once()
        await llm.aclose()

    asyncio.run(run())


def test_stream_uses_backup_model_after_exhausting_retries():
    models = []

    def handler(request):
        model = json.loads(request.content)["model"]
        models.append(model)
        return httpx.Response(503, json={"error": {"message": "temporary outage"}}) if model == "primary" else stream_response()

    llm = make_llm(handler, retries=0)

    async def run():
        assert [token async for token in llm.streamText("hello")] == ["Hello"]
        assert models == ["primary", "backup"]
        await llm.aclose()

    asyncio.run(run())


def test_stream_does_not_restart_an_answer_after_content_was_sent():
    requests = []

    class InterruptedStream(httpx.AsyncByteStream):
        async def __aiter__(self):
            yield b'data: {"choices":[{"delta":{"content":"Hello"}}]}\n\n'
            raise httpx.ReadError("connection lost")

    def handler(request):
        requests.append(request)
        return httpx.Response(200, stream=InterruptedStream())

    llm = make_llm(handler)

    async def run():
        chunks = []
        with pytest.raises(LLMClientError, match="connection lost"):
            async for token in llm.streamText("hello"):
                chunks.append(token)
        assert chunks == ["Hello"]
        assert len(requests) == 1
        await llm.aclose()

    asyncio.run(run())
