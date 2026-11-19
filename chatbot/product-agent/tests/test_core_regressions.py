import asyncio
import hashlib
import hmac
import json
from types import SimpleNamespace

from app.schemas.chat import ChatRequest
from app.schemas.product import ProductRead
from app.schemas.query import Intent, UnderstandingResult
from app.schemas.retrieval import RerankedCandidate, RetrievalResult, ScoredCandidate
from app.repositories.vector_repository import VectorRepository
from app.services.agent import orchestrator
from app.services.agent.generation import generationService
from app.services.agent.dialogue import ConversationContext, RedisSessionStore
from app.services.retrieval import search
from app.services.ingestion.pipeline import ProductChunker


def test_vector_search_keeps_embedding_space_when_relaxing_filters():
    repository = VectorRepository(url=":memory:", dimension=2)
    chunk = ProductChunker.chunk(product(42, 120000).model_dump(by_alias=True))[0]
    assert repository.upsertChunks([chunk], [[]], "model-a") == 0
    assert repository.upsertChunks([chunk], [[1.0, 0.0]], "model-a") == 1
    assert not repository.searchVectors([1.0, 0.0], brand="missing", embeddingSpace="model-b")
    assert repository.searchVectors([1.0, 0.0], brand="missing", embeddingSpace="model-a")


def test_outside_domain_reply_is_contextual_and_does_not_echo_model_text(monkeypatch):
    async def classify(**_):
        return "Bạn đang muốn biết đội nào vô địch World Cup, nhưng mình chưa thể hỗ trợ câu hỏi này."

    monkeypatch.setattr(orchestrator.llmClient, "generateText", classify)
    reply = asyncio.run(orchestrator.outside_domain_reply("Ai vô địch World Cup?"))
    assert reply.startswith("Bạn đang muốn biết đội nào vô địch World Cup")
    assert "trợ lý AI của BeautyShop" in reply

    async def invalid(**_):
        return "Đội X vô địch World Cup."

    monkeypatch.setattr(orchestrator.llmClient, "generateText", invalid)
    reply = asyncio.run(orchestrator.outside_domain_reply("Ai vô địch World Cup?"))
    assert "Đội X" not in reply
    assert "thông tin thể thao" in reply
    assert "trợ lý AI của BeautyShop" in reply


def test_fast_path_defers_weather_mentioned_in_catalog_request():
    parser = orchestrator.fastPathParser
    assert parser.tryParse("Trời nắng thì shop có kem chống nắng nào cho da dầu không?", None) is None
    assert parser.tryParse("2+2 bằng mấy?", None).intent == Intent.OUT_OF_DOMAIN


def product(id, price, ingredients="Glycerin"):
    return ProductRead(
        id=f"mysql_{id}", name=f"Product {id}", brand=f"Brand {id}",
        category="serum", price=price, ingredients=ingredients,
        description="", benefits="", usage="",
    )


def test_live_price_and_stock_filter_before_rerank(monkeypatch):
    products = [product(1, 100000), product(2, 120000), product(3, 155000), product(4, 90000, "Parfum")]

    async def retrieve(*args, **kwargs):
        return RetrievalResult(
            query="serum", candidates=[
                ScoredCandidate(productId=p.id, product=p, rrfScore=1 / index)
                for index, p in enumerate(products, 1)
            ],
        )

    understanding = UnderstandingResult(
        intent=Intent.PRODUCT_SEARCH, category="serum", raw_query="serum",
        constraints={"max_price": 170000}, negative_preferences={"fragrance_free": True},
        target_quantity=1,
    )
    monkeypatch.setattr(orchestrator.fastPathParser, "tryParse", lambda *_: understanding)
    monkeypatch.setattr(orchestrator.queryRouter, "shouldRetrieve", lambda *_: True)
    monkeypatch.setattr(orchestrator.retrievalService, "retrieve", retrieve)
    monkeypatch.setattr(orchestrator.retrievalService.productRepo, "listIdentities", lambda: [])
    monkeypatch.setattr(
        orchestrator.dbExtractor, "getAvailableProductPrices", lambda _: {2: 190000, 3: 160000, 4: 90000}
    )
    response = asyncio.run(orchestrator.agentOrchestrator.preparePipeline(
        ChatRequest(session_id="stock-test", message="serum"), orchestrator.LatencyTracker("test"),
    ))
    assert [(row.product.id, row.product.price) for row in response.rerankedCandidates] == [("mysql_3", 160000)]


def test_stream_uses_verified_fallback_and_catalog_price(monkeypatch):
    p = product(5, 267000)
    candidate = RerankedCandidate(productId=p.id, product=p, initialScore=1, rerankScore=1)

    async def bad_generation(**kwargs):
        return "Product 5 [1] co gia 300.000 VND [1]."

    monkeypatch.setattr("app.services.agent.generation.llmClient.generateText", bad_generation)

    async def collect():
        return [piece async for piece in generationService.generateStream(
            userMessage="serum gia bao nhieu", candidates=[candidate],
            understanding=UnderstandingResult(intent=Intent.PRICE_QUERY),
        )]

    output = "".join(asyncio.run(collect()))
    assert "300.000" not in output
    assert "267,000 VND" in output


def test_one_retrieval_source_can_fail_without_losing_sql_results(monkeypatch):
    def failed(*args, **kwargs):
        raise RuntimeError("Source unavailable")

    async def embed(*args, **kwargs):
        raise RuntimeError("Embedding unavailable")

    monkeypatch.setattr(search.retrievalService.productRepo, "filterProducts", lambda **_: [product(9, 90000)])
    monkeypatch.setattr(search.bm25Service, "search", failed)
    monkeypatch.setattr(search.embeddingService, "embedQuery", embed)
    result = asyncio.run(search.retrievalService.retrieve(
        UnderstandingResult(intent=Intent.PRODUCT_SEARCH, raw_query="serum")
    ))
    assert [row.product.id for row in result.candidates] == ["mysql_9"]


def test_signed_context_is_stable_and_unsigned_context_is_isolated(monkeypatch):
    monkeypatch.setattr("app.schemas.chat.getSettings", lambda: SimpleNamespace(chatbot_context_secret="unit-secret"))
    key = "a" * 64
    signature = hmac.new(b"unit-secret", ("context:" + key).encode(), hashlib.sha256).hexdigest()
    valid = ChatRequest(session_id="public", message="hello", context_id=f"{key}.{signature}")
    assert valid.context_key == "scoped:" + key
    one = ChatRequest(session_id="public", message="hello")
    two = ChatRequest(session_id="public", message="hello", context_id=f"{key}.invalid")
    assert one.context_key != two.context_key
    assert one.context_key == one.context_key


def test_order_lookup_does_not_record_verification_details(monkeypatch):
    phone = "0901234567"
    code = "ORD-" + "1234ABCD" * 4
    monkeypatch.setattr(orchestrator.queryRouter, "handleOrderLookup", lambda raw: ("Verified order", None))
    request = ChatRequest(session_id="order-proof", message=f"{code} {phone}")
    response = asyncio.run(orchestrator.agentOrchestrator.handleChat(request))
    assert response.intent == "ORDER_STATUS"
    assert response.sessionId == "order-proof"
    assert not orchestrator.conversationManager.getOrCreate(request.context_key).turns
    assert phone not in orchestrator.redact_order_proof(request.message)
    assert code not in orchestrator.redact_order_proof(request.message)


def test_redis_context_store_round_trips_with_ttl():
    data = {}

    class StubRedis:
        def set(self, key, value, *, ex):
            assert ex == 3600
            data[key] = value

        def getex(self, key, *, ex):
            assert ex == 3600
            return data.get(key)

    store = RedisSessionStore.__new__(RedisSessionStore)
    store.client = StubRedis()
    store.ttlSeconds = 3600
    context = ConversationContext(sessionId="stored")
    context.accumulatedConstraints["max_price"] = 175000
    store.set("stored", context)
    assert store.get("stored").accumulatedConstraints == {"max_price": 175000}
    assert store.get("other") is None
    assert json.loads(next(iter(data.values())))["sessionId"] == "stored"
