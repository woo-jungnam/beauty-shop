from app.schemas.chat import SourceRef
from app.schemas.product import ProductRead
from app.schemas.query import UnderstandingResult, Intent, PrimaryIntent
from app.schemas.retrieval import RerankedCandidate
from app.services.agent.generation import ContextBuilder
from app.services.agent.guardrail import generationGuardrail
from app.services.agent.orchestrator import AgentOrchestrator

def test_rag_improvements():
    print("=== KIỂM THỬ KHẮC PHỤC RAG 1 & 2 ===")

    prod1 = ProductRead(
        id="mysql_1",
        name="Kem Dưỡng La Roche-Posay Cicaplast Baume B5+",
        brand="La Roche-Posay",
        category="cream",
        price=385000.0,
        skinType=["dry", "sensitive"],
        concerns=["irritation", "redness"],
        ingredients="Aqua, Hydrogenated Polyisobutene, Dimethicone, Glycerin, Panthenol 5%, Madecassoside",
        benefits="Làm dịu da tức thì, phục hồi màng bảo vệ da",
        usage="Thoa 2 lần mỗi ngày",
        rating=4.9,
        totalReviews=1200,
        totalSold=3500,
        targetType="PRODUCT"
    )

    cand1 = RerankedCandidate(
        productId=prod1.id,
        product=prod1,
        initialScore=0.9,
        rerankScore=15.0,
        relevanceExplanation="Phục hồi tốt cho da nhạy cảm"
    )

    # 1. Test Selective Context Builder - PRICE_QUERY
    und_price = UnderstandingResult.model_validate({
        "raw_query": "Kem B5 giá bao nhiêu?",
        "intent": Intent.PRICE_QUERY,
        "primary_intent": PrimaryIntent.GIA_NGAN_SACH.value
    })
    ctx_price = ContextBuilder.buildProductContext([cand1], understanding=und_price)
    print("\n--- [Context PRICE_QUERY] ---")
    print(ctx_price)
    assert "[Tài liệu 1]" in ctx_price
    assert "385,000 VND" in ctx_price
    assert "Còn hàng" in ctx_price
    assert "Aqua, Hydrogenated" not in ctx_price  # Lược bỏ tá dược
    print("-> Test Context PRICE_QUERY: PASSED!")

    # 2. Test Selective Context Builder - INGREDIENT_QUERY
    und_ingr = UnderstandingResult.model_validate({
        "raw_query": "B5+ có thành phần gì?",
        "intent": Intent.PRODUCT_DETAIL,
        "primary_intent": PrimaryIntent.THONG_TIN_THANH_PHAN.value
    })
    ctx_ingr = ContextBuilder.buildProductContext([cand1], understanding=und_ingr)
    print("\n--- [Context INGREDIENT_QUERY] ---")
    print(ctx_ingr)
    assert "[Tài liệu 1]" in ctx_ingr
    assert "Panthenol 5%" in ctx_ingr
    assert "385,000 VND" not in ctx_ingr  # Lược bỏ giá khi chỉ hỏi thành phần
    print("-> Test Context INGREDIENT_QUERY: PASSED!")

    # 3. Test In-text Citation formatting in SourceRef
    sources = AgentOrchestrator.formatSources([cand1])
    assert len(sources) == 1
    assert sources[0].index == 1
    assert sources[0].productName == prod1.name
    print("\n--- [SourceRef Indexing] ---")
    print(f"Source 0: index={sources[0].index}, name={sources[0].productName}")
    print("-> Test SourceRef Indexing: PASSED!")

    # 4. Test Guardrail citation validation
    resp_valid = "Kem dưỡng B5+ [1] có giá 385.000đ [1] rất tốt cho da khô."
    is_valid, sanitized, issues = generationGuardrail.verifyResponse(resp_valid, [cand1])
    assert is_valid
    assert "[1]" in sanitized
    assert len(issues) == 0
    print("\n--- [Guardrail Valid Citation] ---")
    print(f"Sanitized: {sanitized}")

    resp_invalid_cite = "Kem dưỡng B5+ [1] và kem chống nắng [99] tuyệt vời."
    is_valid_inv, sanitized_inv, issues_inv = generationGuardrail.verifyResponse(resp_invalid_cite, [cand1])
    assert "[99]" not in sanitized_inv  # Đã tự động loại bỏ thẻ nguồn không tồn tại
    assert any("Invalid citation indices" in s for s in issues_inv)
    print("\n--- [Guardrail Invalid Citation Stripping] ---")
    print(f"Stripped response: {sanitized_inv}")
    print(f"Issues detected: {issues_inv}")
    print("-> Test Guardrail Citation Validation: PASSED!")

if __name__ == "__main__":
    test_rag_improvements()
