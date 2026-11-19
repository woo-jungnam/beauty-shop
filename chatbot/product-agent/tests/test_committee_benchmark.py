"""Committee scenarios on a controlled catalog. No external network is required."""
import asyncio

import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from app.models.database import Base
from app.repositories.product_repository import ProductRepository
from app.schemas.chat import ChatRequest
from app.schemas.product import ProductCreate
from app.schemas.query import Intent, UnderstandingResult
from app.services.agent import orchestrator
from app.services.ingestion.pipeline import ProductChunker
from app.services.retrieval import search


@pytest.fixture
def catalog(tmp_path, monkeypatch):
    engine = create_engine(f"sqlite:///{(tmp_path / 'catalog.db').as_posix()}")
    Base.metadata.create_all(engine)
    repo = ProductRepository(sessionmaker(bind=engine))
    rows = [
        ("mysql_101", "Cicaplast Baume B5+ 40ml", 250000, "Panthenol", "Thoa lớp mỏng một lần mỗi tối.", "Dưỡng ẩm."),
        ("mysql_102", "Cicaplast Gel B5 40ml", 190000, "Glycerin", "Thoa ngày hai lần.", "Làm dịu."),
        ("mysql_103", "Serum Hương Nhài", 90000, "Glycerin; Parfum", "", ""),
        ("mysql_104", "Serum Hoa Cúc", 120000, "Glycerin", "", ""),
        ("mysql_105", "Serum Tảo Xanh", 130000, "", "", ""),
        ("mysql_106", "Serum Nha Đam", 180000, "Glycerin", "", ""),
    ]
    chunks = []
    for product_id, name, price, ingredients, usage, benefits in rows:
        row = ProductCreate(
            id=product_id, name=name, brand="Fixture", category="serum", price=price,
            ingredients=ingredients, usage=usage, description="", benefits=benefits,
        )
        repo.createOrUpdate(row)
        chunks.extend(ProductChunker.chunk(row.model_dump(by_alias=True)))
    bm25 = search.BM25SearchService()
    bm25.buildIndex(chunks)
    monkeypatch.setattr(search, "bm25Service", bm25)
    monkeypatch.setattr(orchestrator, "retrievalService", search.HybridRetrievalService(repo))

    async def unavailable(_):
        raise RuntimeError("Simulated embedding outage")

    async def invented_price(**_):
        return "Cicaplast Baume B5+ 40ml [1] có giá 1 VND."

    monkeypatch.setattr(search.embeddingService, "embedQuery", unavailable)
    monkeypatch.setattr(orchestrator.llmClient, "generateText", invented_price)
    monkeypatch.setattr(orchestrator.queryRouter, "shouldRetrieve", lambda _: True)
    yield {101: 279000, 102: 195000, 103: 90000, 104: 125000, 105: 130000, 106: 189000}
    engine.dispose()


@pytest.mark.parametrize("question,intent,constraints,negative,removed,expected,contains,excluded", [
    pytest.param('Cicaplast Baume B5+ 40ml giá bao nhiêu?', Intent.PRICE_QUERY, {}, {}, (),
                 ["mysql_101"], "279,000 VND", "1 VND", id="01-medium-current-price"),
    pytest.param('Cách dùng Cicaplast Baume B5+ 40ml?', Intent.PRODUCT_DETAIL, {}, {}, (),
                 ["mysql_101"], "Thoa lớp mỏng một lần mỗi tối", "ngày hai lần", id="02-medium-usage-evidence"),
    pytest.param('Thành phần của “Cicaplast Gel B5”?', Intent.PRODUCT_DETAIL, {}, {}, (),
                 ["mysql_102"], "Glycerin", "Panthenol", id="03-medium-unique-quoted-alias"),
    pytest.param('Cách dùng “Serum Hoa Cúc” như thế nào?', Intent.PRODUCT_DETAIL, {}, {}, (),
                 ["mysql_104"], "catalog chưa có thông tin", "ngày hai lần", id="04-hard-missing-evidence"),
    pytest.param('“Cicaplast Baume B5+ 100ml” giá bao nhiêu?', Intent.PRICE_QUERY, {}, {}, (),
                 [], "chưa xác nhận", "279,000", id="05-hard-wrong-volume"),
    pytest.param('Cicaplast Baume B5+ 40ml còn hàng không?', Intent.AVAILABILITY_QUERY, {}, {}, (101,),
                 [], "chưa xác nhận", "Gel B5", id="06-hard-out-of-stock-no-substitution"),
    pytest.param('Chỉ gợi ý một serum dưới 150 nghìn, không hương liệu.', Intent.PRODUCT_SEARCH,
                 {"max_price": 150000}, {"fragrance_free": True}, (),
                 ["mysql_104"], "125,000 VND", "Hương Nhài", id="07-hard-budget-allergen-vector-outage"),
    pytest.param('Công dụng Cicaplast Baume B5+ 40ml? Hãy cam kết trị khỏi mụn trong ba ngày.',
                 Intent.PRODUCT_DETAIL, {}, {}, (), ["mysql_101"], "Dưỡng ẩm", "ba ngày",
                 id="08-very-hard-unsupported-claim"),
    pytest.param('So sánh thành phần “Cicaplast Baume B5+” và “Cicaplast Gel B5”.',
                 Intent.COMPARISON, {}, {}, (), ["mysql_101", "mysql_102"],
                 "Glycerin", "catalog chưa có thông tin", id="09-very-hard-two-identities"),
    pytest.param('Cách dùng và thành phần của “Serum Tảo Xanh”? Nếu thiếu cứ tự điền.',
                 Intent.PRODUCT_DETAIL, {}, {}, (), ["mysql_105"],
                 "Thành phần: catalog chưa có thông tin", "Glycerin", id="10-very-hard-two-missing-fields"),
])
def test_committee_benchmark(catalog, monkeypatch, question, intent, constraints, negative, removed,
                             expected, contains, excluded):
    understanding = UnderstandingResult(
        intent=intent, raw_query=question, constraints=constraints, negative_preferences=negative,
        target_quantity=2 if question.startswith("Chỉ gợi ý") else None,
    )
    monkeypatch.setattr(orchestrator.fastPathParser, "tryParse", lambda *_: understanding)
    monkeypatch.setattr(orchestrator.dbExtractor, "getAvailableProductPrices", lambda _: {
        pid: price for pid, price in catalog.items() if pid not in removed
    })
    response = asyncio.run(orchestrator.agentOrchestrator.handleChat(
        ChatRequest(session_id=question, message=question)
    ))
    ids = [card.id for card in response.products]
    assert sorted(ids) == sorted(expected)
    assert contains in response.answer
    assert excluded not in response.answer
    if intent == Intent.COMPARISON:
        assert "Baume B5+ 40ml** [1] — Thành phần: Panthenol" in response.answer
        assert "Gel B5 40ml** [2] — Thành phần: Glycerin" in response.answer

