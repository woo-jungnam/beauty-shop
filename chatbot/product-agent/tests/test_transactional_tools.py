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
        "order_number": "ORD-20261102-001",
        "status": "PROCESSING",
        "status_vi": "Đang đóng gói chuẩn bị hàng",
        "payment_status": "PAID",
        "payment_status_vi": "Đã thanh toán thành công",
        "customer_name": "Nguyễn Văn A",
        "customer_phone": "0912345678",
        "shipping_address": "123 Lê Lợi, P. Bến Nghé",
        "city": "TP. Hồ Chí Minh",
        "total_amount": 750000.0,
        "items": [
            {
                "product_name": "Serum Centella Skin1004",
                "variant_name": "55ml",
                "quantity": 2,
                "price": 350000.0,
            }
        ],
    }
    summary = repo.formatOrderSummary(mock_order)
    assert "#ORD-20261102-001" in summary
    assert "Đang đóng gói chuẩn bị hàng" in summary
    assert "Nguyễn Văn A (0912345678)" in summary
    assert "750,000đ" in summary


def test_query_router_order_lookup_parsing(monkeypatch):
    router = QueryRouter()

    # Case 1: Không có mã đơn hoặc SĐT
    reply, order = router.handleOrderLookup("tôi muốn hỏi về thời gian làm việc")
    assert "vui lòng cung cấp Mã đơn hàng" in reply
    assert order is None

    # Case 2: Mock order repository findOrder trả về order
    def mock_find_order(identifier: str):
        if identifier in ("ORD-99999", "0987654321"):
            return {
                "order_number": "ORD-99999",
                "status_vi": "Đang giao hàng",
                "payment_status_vi": "Chưa thanh toán (COD)",
                "customer_name": "Trần Thị B",
                "customer_phone": "0987654321",
                "shipping_address": "456 Nguyễn Huệ",
                "city": "Hà Nội",
                "total_amount": 320000.0,
                "items": [],
            }
        return None

    from app.repositories.order_repository import orderRepository
    monkeypatch.setattr(orderRepository, "findOrder", mock_find_order)

    reply_found, order_found = router.handleOrderLookup("kiểm tra giúp đơn hàng ORD-99999")
    assert order_found is not None
    assert "#ORD-99999" in reply_found
    assert "Đang giao hàng" in reply_found

    reply_phone, order_phone = router.handleOrderLookup("tra cứu đơn số điện thoại 0987654321")
    assert order_phone is not None
    assert "0987654321" in reply_phone


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
