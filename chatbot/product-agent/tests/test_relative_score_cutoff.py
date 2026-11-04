import pytest
from app.schemas.product import Product
from app.schemas.query import Intent, PrimaryIntent, UnderstandingResult
from app.schemas.retrieval import ScoredCandidate
from app.services.retrieval.reranker import ProductReranker


def create_product(pid: str, name: str, brand: str, category: str, price: float = 300000.0) -> Product:
    return Product(
        id=pid,
        name=name,
        brand=brand,
        category=category,
        price=price,
        skin_type=["all"],
        concerns=["dưỡng da"],
        ingredients="Nước, Glycerin",
        benefits="Dưỡng ẩm",
    )


def test_relative_score_cutoff_single_match():
    reranker = ProductReranker()

    # Product A: Serum Rau Má Skin1004 (khớp hoàn hảo query)
    prod_a = create_product("prod_a", "Serum Rau Má Skin1004 Madagascar Centella", "Skin1004", "serum")
    # Product B: Son môi 3CE (không liên quan)
    prod_b = create_product("prod_b", "Son Thỏi 3CE Mood Recipe", "3CE", "Son môi")
    # Product C: Sữa tắm Enchanteur (không liên quan)
    prod_c = create_product("prod_c", "Sữa Tắm Nước Hoa Enchanteur", "Enchanteur", "Sữa tắm")

    # Giả lập ScoredCandidate từ retrieval
    cand_a = ScoredCandidate(productId="prod_a", product=prod_a, rrfScore=0.05)
    cand_b = ScoredCandidate(productId="prod_b", product=prod_b, rrfScore=0.01)
    cand_c = ScoredCandidate(productId="prod_c", product=prod_c, rrfScore=0.01)

    und = UnderstandingResult(
        intent=Intent.PRODUCT_SEARCH,
        primary_intent=PrimaryIntent.TU_VAN_SAN_PHAM.value,
        category="serum",
        brand="Skin1004",
        raw_query="Tìm serum rau má Skin1004",
        semantic_query="serum rau má skin1004 làm dịu da",
        bm25_query="serum rau má skin1004",
        constraints={"category": "serum", "brand": "Skin1004"},
    )

    results = reranker.rerank([cand_a, cand_b, cand_c], understanding=und, topK=3)

    # Sản phẩm B và C bị phạt danh mục và rơi điểm tương đối -> chỉ còn lại duy nhất sản phẩm A
    assert len(results) == 1
    assert results[0].productId == "prod_a"
    assert results[0].product.name == "Serum Rau Má Skin1004 Madagascar Centella"


def test_relative_score_keep_all_when_all_relevant():
    reranker = ProductReranker()

    # 3 kem chống nắng cùng phù hợp
    prod_a = create_product("prod_a", "Kem Chống Nắng Anessa Perfect UV Sunscreen", "Anessa", "kem chống nắng")
    prod_b = create_product("prod_b", "Kem Chống Nắng La Roche-Posay Anthelios", "La Roche-Posay", "kem chống nắng")
    prod_c = create_product("prod_c", "Kem Chống Nắng Skin1004 Centella Air-Fit", "Skin1004", "kem chống nắng")

    cand_a = ScoredCandidate(productId="prod_a", product=prod_a, rrfScore=0.05)
    cand_b = ScoredCandidate(productId="prod_b", product=prod_b, rrfScore=0.045)
    cand_c = ScoredCandidate(productId="prod_c", product=prod_c, rrfScore=0.042)

    und = UnderstandingResult(
        intent=Intent.PRODUCT_SEARCH,
        primary_intent=PrimaryIntent.TU_VAN_SAN_PHAM.value,
        category="kem chống nắng",
        raw_query="Gợi ý kem chống nắng kiềm dầu tốt",
        semantic_query="kem chống nắng kiềm dầu",
        bm25_query="kem chống nắng kiềm dầu",
        constraints={"category": "kem chống nắng"},
    )

    results = reranker.rerank([cand_a, cand_b, cand_c], understanding=und, topK=3)

    # Cả 3 sản phẩm đều đạt điểm cao và tỉ số > 0.60 so với top 1 -> giữ nguyên cả 3
    assert len(results) == 3
    result_ids = [r.productId for r in results]
    assert "prod_a" in result_ids
    assert "prod_b" in result_ids
    assert "prod_c" in result_ids


def test_relative_score_cutoff_comparison_intent():
    reranker = ProductReranker()

    # 2 sản phẩm so sánh + 1 sản phẩm rác
    prod_a = create_product("prod_a", "Serum B5 La Roche-Posay Hyalu B5", "La Roche-Posay", "serum")
    prod_b = create_product("prod_b", "Serum B5 GoodnDoc Hydra B5", "GoodnDoc", "serum")
    prod_c = create_product("prod_c", "Chì kẻ mày Innisfree", "Innisfree", "Chì kẻ mày")

    cand_a = ScoredCandidate(productId="prod_a", product=prod_a, rrfScore=0.05)
    cand_b = ScoredCandidate(productId="prod_b", product=prod_b, rrfScore=0.04)
    cand_c = ScoredCandidate(productId="prod_c", product=prod_c, rrfScore=0.01)

    und = UnderstandingResult(
        intent=Intent.COMPARISON,
        primary_intent=PrimaryIntent.SO_SANH_SAN_PHAM.value,
        category="serum",
        raw_query="So sánh serum B5 La Roche-Posay và GoodnDoc",
        semantic_query="so sánh serum b5 la roche-posay và goodndoc",
        bm25_query="serum b5 la roche-posay goodndoc",
        constraints={"category": "serum"},
    )

    results = reranker.rerank([cand_a, cand_b, cand_c], understanding=und, topK=3)

    # Giữ đúng 2 sản phẩm so sánh, cắt bỏ sản phẩm thứ 3
    assert len(results) == 2
    assert results[0].productId == "prod_a"
    assert results[1].productId == "prod_b"


def test_empty_results_when_all_penalized():
    reranker = ProductReranker()

    # Toàn bộ sản phẩm đều vi phạm ngân sách cực lớn hoặc sai danh mục hoàn toàn
    prod_x = create_product("prod_x", "Sản phẩm A", "BrandX", "makeup", price=1000000.0)
    cand_x = ScoredCandidate(productId="prod_x", product=prod_x, rrfScore=0.01)

    und = UnderstandingResult(
        intent=Intent.PRODUCT_SEARCH,
        primary_intent=PrimaryIntent.TU_VAN_SAN_PHAM.value,
        category="skincare",
        constraints={"max_price": 100000.0, "category": "skincare"},
    )

    results = reranker.rerank([cand_x], understanding=und, topK=3)
    # Không còn bị fallback ép nhặt sản phẩm điểm âm
    assert len(results) == 0
