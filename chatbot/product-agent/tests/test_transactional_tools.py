import pytest
from app.repositories.order_repository import OrderRepository
from app.schemas.chat import ChatAction, ChatResponse, ProductCard
from app.schemas.product import Product
from app.schemas.query import Intent, PrimaryIntent, UnderstandingResult
from app.schemas.retrieval import CandidateScore, RerankedCandidate
from app.services.agent.orchestrator import AgentOrchestrator
from app.services.agent.router import FastPathParser, QueryRouter


def test_chat_action_schema():
    action = ChatAction(
        action_type="ADD_TO_CART",
        label="Thêm Serum Skin1004 vào giỏ",
        payload={"product_id": "101", "price": 350000},
    )
    dumped = action.model_dump(by_alias=True)
    assert dumped["action_type"] == "ADD_TO_CART"
    assert dumped["label"] == "Thêm Serum Skin1004 vào giỏ"
    assert dumped["payload"]["product_id"] == "101"

    res = ChatResponse(
        sessionId="sess_1",
        intent="PRODUCT_SEARCH",
        answer="Chào bạn",
        actions=[action],
    )
    res_dump = res.model_dump(by_alias=True)
    assert len(res_dump["actions"]) == 1
    assert res_dump["actions"][0]["action_type"] == "ADD_TO_CART"


def test_order_repository_formatting():
    repo = OrderRepository()
    mock_order = {
        "order_number": "ORD-A1B2C3D4E5F678901234567890ABCDEF",
        "status": "PROCESSING",
        "payment_status": "PAID",
        "customer_phone": "0912345678",
    }
    summary = repo.formatOrderSummary(mock_order)
    assert "#ORD-A1B2C3D4E5F678901234567890ABCDEF" in summary
    assert "Đang đóng gói chuẩn bị hàng" in summary
    assert "Đã thanh toán thành công" in summary
    assert "******5678" in summary
    # Đảm bảo không chứa PII địa chỉ, tên khách, tổng tiền hay danh sách items
    assert "Nguyễn Văn" not in summary
    assert "Lê Lợi" not in summary
    assert "750,000" not in summary


def test_query_router_order_lookup_parsing(monkeypatch):
    router = QueryRouter()

    # Case 1: Không có mã đơn hoặc SĐT -> yêu cầu cung cấp cả hai
    reply, order = router.handleOrderLookup("tôi muốn hỏi về thời gian làm việc")
    assert "cung cấp đồng thời Mã đơn hàng" in reply
    assert order is None

    # Case 2: Chỉ có mã đơn mà không có SĐT -> từ chối tra cứu đơn lẻ
    reply_only_code, order_only_code = router.handleOrderLookup("kiểm tra giúp đơn hàng ORD-A1B2C3D4E5F678901234567890ABCDEF")
    assert order_only_code is None
    assert "cung cấp đồng thời Mã đơn hàng" in reply_only_code

    # Case 3: Chỉ có SĐT mà không có mã đơn -> từ chối tra cứu đơn lẻ (chống PII scraping)
    reply_only_phone, order_only_phone = router.handleOrderLookup("tra cứu đơn số điện thoại 0987654321")
    assert order_only_phone is None
    assert "cung cấp đồng thời Mã đơn hàng" in reply_only_phone

    # Case 4: Có đủ cả mã đơn và SĐT hợp lệ
    def mock_find_order(order_number: str, customer_phone: str):
        if order_number == "ORD-A1B2C3D4E5F678901234567890ABCDEF" and customer_phone == "0987654321":
            return {
                "id": 1,
                "order_number": "ORD-A1B2C3D4E5F678901234567890ABCDEF",
                "status": "SHIPPING",
                "payment_status": "PENDING",
                "customer_phone": "******4321",
            }
        return None

    from app.repositories.order_repository import orderRepository
    monkeypatch.setattr(orderRepository, "findOrder", mock_find_order)

    # Đầy đủ cặp mã đơn và số điện thoại
    query_text = "kiểm tra đơn ORD-A1B2C3D4E5F678901234567890ABCDEF sđt 0987654321"
    reply_found, order_found = router.handleOrderLookup(query_text)
    assert order_found is not None
    assert "#ORD-A1B2C3D4E5F678901234567890ABCDEF" in reply_found
    assert "Đang giao hàng" in reply_found

    # Hỗ trợ đầu số +84
    query_plus84 = "đơn ORD-A1B2C3D4E5F678901234567890ABCDEF sđt +84987654321"
    reply_84, order_84 = router.handleOrderLookup(query_plus84)
    assert order_84 is not None
    assert "Đang giao hàng" in reply_84

    # Sai số điện thoại hoặc mã đơn không khớp
    query_mismatch = "kiểm tra đơn ORD-A1B2C3D4E5F678901234567890ABCDEF sđt 0912345678"
    reply_mismatch, order_mismatch = router.handleOrderLookup(query_mismatch)
    assert order_mismatch is None
    assert "Không tìm thấy đơn hàng khớp với mã đơn và số điện thoại" in reply_mismatch


def test_fast_path_order_status():
    parser = FastPathParser()
    res1 = parser.tryParse("tra cứu đơn hàng ORD-123456", None)
    assert res1 is not None
    assert res1.intent == Intent.ORDER_STATUS
    assert res1.primary_intent == PrimaryIntent.HO_TRO_DON_HANG.value

    res2 = parser.tryParse("kiểm tra tình trạng đơn #DH12345", None)
    assert res2 is not None
    assert res2.intent == Intent.ORDER_STATUS

    res3 = parser.tryParse("#ORD-987654", None)
    assert res3 is not None
    assert res3.intent == Intent.ORDER_STATUS


def test_agent_orchestrator_generate_actions():
    orchestrator = AgentOrchestrator()

    # 1. Ứng viên sản phẩm -> sinh ADD_TO_CART
    prod = Product(
        id="mysql_105",
        name="Kem Chống Nắng Anessa Perfect UV Sunscreen",
        brand="Anessa",
        category="sunscreen",
        price=550000.0,
        skin_type=["all"],
        concerns=["chống nắng"],
        ingredients="Zinc Oxide, Titanium Dioxide",
        benefits="Bảo vệ da vượt trội",
    )
    cand_prod = RerankedCandidate(
        product=prod,
        score=0.9,
        retrievalScore=CandidateScore(productId="mysql_105", finalScore=0.9),
    )

    und_prod = UnderstandingResult(
        intent=Intent.PRODUCT_SEARCH,
        primary_intent=PrimaryIntent.TU_VAN_SAN_PHAM.value,
    )
    actions = orchestrator.generateActions(und_prod, [cand_prod], "tư vấn kem chống nắng")
    assert any(a.actionType == "ADD_TO_CART" for a in actions)
    cart_act = next(a for a in actions if a.actionType == "ADD_TO_CART")
    assert cart_act.payload["product_id"] == "105"
    assert cart_act.payload["price"] == 550000.0

    # 2. Ứng viên dịch vụ Spa -> sinh BOOKING_PROPOSAL
    svc = Product(
        id="service_20",
        name="Liệu trình Peel Da Sinh Học Trị Mụn",
        brand="BeautySpa",
        category="spa_service",
        price=650000.0,
        skin_type=["oily"],
        concerns=["mụn"],
        ingredients="Salicylic Acid, Glycolic Acid",
        benefits="Giảm mụn gom cồi",
        target_type="SERVICE",
        duration_minutes=60,
    )
    cand_svc = RerankedCandidate(
        product=svc,
        score=0.95,
        retrievalScore=CandidateScore(productId="service_20", finalScore=0.95),
    )

    und_svc = UnderstandingResult(
        intent=Intent.PRODUCT_SEARCH,
        primary_intent=PrimaryIntent.DAT_LICH.value,
    )
    actions_svc = orchestrator.generateActions(und_svc, [cand_svc], "mình muốn đặt lịch peel da")
    assert any(a.actionType == "BOOKING_PROPOSAL" for a in actions_svc)
    book_act = next(a for a in actions_svc if a.actionType == "BOOKING_PROPOSAL")
    assert book_act.payload["service_id"] == "20"
    assert book_act.payload["duration_minutes"] == 60
