import re
from enum import Enum
from typing import Any
from pydantic import BaseModel, Field, model_validator


class ComplexityLevel(str, Enum):
    MUC_1_DON_GIAN = "MỨC_1_ĐƠN_GIẢN"
    MUC_2_NGU_NGHIA = "MỨC_2_NGỮ_NGHĨA"
    MUC_3_NHIEU_RANG_BUOC = "MỨC_3_NHIỀU_RÀNG_BUỘC"
    MUC_4_NHIEU_LUOT_HOI_THOAI = "MỨC_4_NHIỀU_LƯỢT_HỘI_THOẠI"
    MUC_5_SUY_LUAN_PHUC_TAP = "MỨC_5_SUY_LUẬN_PHỨC_TẠP"


class Goal(str, Enum):
    TIM_THONG_TIN = "TÌM_THÔNG_TIN"
    TIM_SAN_PHAM = "TÌM_SẢN_PHẨM"
    DIEU_TRI = "ĐIỀU_TRỊ"
    SO_SANH = "SO_SÁNH"
    KIEM_TRA = "KIỂM_TRA"
    GIAI_THICH = "GIẢI_THÍCH"
    DANH_GIA = "ĐÁNH_GIÁ"
    XAY_DUNG = "XÂY_DỰNG"
    DAT_LICH = "ĐẶT_LỊCH"


class PrimaryIntent(str, Enum):
    THONG_TIN_SAN_PHAM = "THÔNG_TIN_SẢN_PHẨM"
    HOI_DAP_SAN_PHAM = "HỎI_ĐÁP_SẢN_PHẨM"
    THONG_TIN_THANH_PHAN = "THÔNG_TIN_THÀNH_PHẦN"
    TAC_DUNG_THANH_PHAN = "TÁC_DỤNG_THÀNH_PHẦN"
    TUONG_THICH_THANH_PHAN = "TƯƠNG_THÍCH_THÀNH_PHẦN"
    PHU_HOP_LAN_DA = "PHÙ_HỢP_LÀN_DA"
    VAN_DE_DA = "VẤN_ĐỀ_DA"
    TU_VAN_SAN_PHAM = "TƯ_VẤN_SẢN_PHẨM"
    SO_SANH_SAN_PHAM = "SO_SÁNH_SẢN_PHẨM"
    XAY_DUNG_CHU_TRINH = "XÂY_DỰNG_CHU_TRINH"
    PHAN_TICH_CHU_TRINH = "PHÂN_TÍCH_CHU_TRÌNH"
    GIA_NGAN_SACH = "GIÁ_NGÂN_SÁCH"
    HO_TRO_DON_HANG = "HỖ_TRỢ_ĐƠN_HÀNG"
    DICH_VU_LAM_DEP = "DỊCH_VỤ_LÀM_ĐẸP"
    DAT_LICH = "ĐẶT_LỊCH"
    CHAO_HOI = "CHÀO_HỎI"
    KHONG_XAC_DINH = "KHÔNG_XÁC_ĐỊNH"


class SubIntent(str, Enum):
    TRI_MUN = "TRỊ_MỤN"
    GIAM_THAM = "GIẢM_THÂM"
    DUONG_AM = "DƯỠNG_ẨM"
    CHONG_LAO_HOA = "CHỐNG_LÃO_HÓA"
    LAM_SANG_DA = "LÀM_SÁNG_DA"
    KIEM_SOAT_DAU = "KIỂM_SOÁT_DẦU"
    PHUC_HOI_DA = "PHỤC_HỒI_DA"
    CHONG_NANG = "CHỐNG_NẮNG"
    TRA_CUU_VAN_CHUYEN = "TRA_CỨU_VẬN_CHUYỂN"
    HUY_DON_HANG = "HỦY_ĐƠN_HÀNG"
    DOI_TRA_BAO_HANH = "ĐỔI_TRẢ_BẢO_HÀNH"
    HINH_THUC_THANH_TOAN = "HÌNH_THỨC_THANH_TOÁN"
    SOI_DA_TU_VAN = "SOI_DA_TƯ_VẤN"
    LIEU_TRINH_MUN = "LIỆU_TRÌNH_MỤN"
    PEEL_DA_SINH_HOC = "PEEL_DA_SINH_HỌC"
    PHUC_HOI_CHUYEN_SAU = "PHỤC_HỒI_CHUYÊN_SÂU"


class ActionRouting(str, Enum):
    FAST_PATH = "FAST_PATH"
    DIRECT_DATABASE_LOOKUP = "DIRECT_DATABASE_LOOKUP"
    CONSTRAINED_HYBRID_SEARCH = "CONSTRAINED_HYBRID_SEARCH"
    CHAIN_OF_THOUGHT_REASONING = "CHAIN_OF_THOUGHT_REASONING"
    CLARIFICATION_PROMPT = "CLARIFICATION_PROMPT"


class Intent(str, Enum):
    PRODUCT_SEARCH = "PRODUCT_SEARCH"
    PRODUCT_RECOMMENDATION = "PRODUCT_SEARCH"
    PRODUCT_DETAIL = "PRODUCT_DETAIL"
    PRICE_QUERY = "PRICE_QUERY"
    AVAILABILITY_QUERY = "AVAILABILITY_QUERY"
    COMPARISON = "COMPARISON"
    ROUTINE_RECOMMENDATION = "ROUTINE_RECOMMENDATION"
    GENERAL_INFORMATION = "GENERAL_INFORMATION"
    GENERAL_CHAT = "GENERAL_CHAT"
    ORDER_STATUS = "ORDER_STATUS"
    UNKNOWN = "UNKNOWN"


UserIntent = Intent


PRIMARY_TO_LEGACY_INTENT = {
    PrimaryIntent.TU_VAN_SAN_PHAM.value: Intent.PRODUCT_SEARCH,
    PrimaryIntent.THONG_TIN_SAN_PHAM.value: Intent.PRODUCT_DETAIL,
    PrimaryIntent.HOI_DAP_SAN_PHAM.value: Intent.PRODUCT_DETAIL,
    PrimaryIntent.GIA_NGAN_SACH.value: Intent.PRICE_QUERY,
    PrimaryIntent.SO_SANH_SAN_PHAM.value: Intent.COMPARISON,
    PrimaryIntent.XAY_DUNG_CHU_TRINH.value: Intent.ROUTINE_RECOMMENDATION,
    PrimaryIntent.PHAN_TICH_CHU_TRINH.value: Intent.ROUTINE_RECOMMENDATION,
    PrimaryIntent.THONG_TIN_THANH_PHAN.value: Intent.GENERAL_INFORMATION,
    PrimaryIntent.TAC_DUNG_THANH_PHAN.value: Intent.GENERAL_INFORMATION,
    PrimaryIntent.TUONG_THICH_THANH_PHAN.value: Intent.COMPARISON,
    PrimaryIntent.PHU_HOP_LAN_DA.value: Intent.PRODUCT_DETAIL,
    PrimaryIntent.VAN_DE_DA.value: Intent.PRODUCT_SEARCH,
    PrimaryIntent.HO_TRO_DON_HANG.value: Intent.ORDER_STATUS,
    PrimaryIntent.DICH_VU_LAM_DEP.value: Intent.PRODUCT_SEARCH,
    PrimaryIntent.DAT_LICH.value: Intent.PRODUCT_SEARCH,
    PrimaryIntent.CHAO_HOI.value: Intent.GENERAL_CHAT,
    PrimaryIntent.KHONG_XAC_DINH.value: Intent.UNKNOWN,
}

LEGACY_TO_PRIMARY_INTENT = {
    Intent.PRODUCT_SEARCH: PrimaryIntent.TU_VAN_SAN_PHAM.value,
    Intent.PRODUCT_DETAIL: PrimaryIntent.THONG_TIN_SAN_PHAM.value,
    Intent.PRICE_QUERY: PrimaryIntent.GIA_NGAN_SACH.value,
    Intent.AVAILABILITY_QUERY: PrimaryIntent.THONG_TIN_SAN_PHAM.value,
    Intent.COMPARISON: PrimaryIntent.SO_SANH_SAN_PHAM.value,
    Intent.ROUTINE_RECOMMENDATION: PrimaryIntent.XAY_DUNG_CHU_TRINH.value,
    Intent.GENERAL_INFORMATION: PrimaryIntent.HOI_DAP_SAN_PHAM.value,
    Intent.GENERAL_CHAT: PrimaryIntent.CHAO_HOI.value,
    Intent.ORDER_STATUS: PrimaryIntent.HO_TRO_DON_HANG.value,
    Intent.UNKNOWN: PrimaryIntent.KHONG_XAC_DINH.value,
}


class Entities(BaseModel):
    target_type: str = Field(default="PRODUCT")
    product_category: str | None = Field(default=None)
    form_factor: str | None = Field(default=None)
    brands: list[str] = Field(default_factory=list)
    services: list[str] = Field(default_factory=list)
    branch: str | None = Field(default=None)
    appointment_time: str | None = Field(default=None)
    skin_type: str | None = Field(default=None)
    target_concerns: list[str] = Field(default_factory=list)
    ingredients_mentioned: list[str] = Field(default_factory=list)
    excluded_ingredients: list[str] = Field(default_factory=list)
    price_mentioned: float | None = Field(default=None)
    target_user: str | None = Field(default=None)

    @property
    def target(self) -> str:
        return self.target_type or "PRODUCT"

    @property
    def targetType(self) -> str:
        return self.target_type or "PRODUCT"

    @property
    def productCategory(self) -> str | None:
        return self.product_category

    @property
    def formFactor(self) -> str | None:
        return self.form_factor

    @property
    def appointmentTime(self) -> str | None:
        return self.appointment_time

    @property
    def skinType(self) -> str | None:
        return self.skin_type

    @property
    def targetConcerns(self) -> list[str]:
        return self.target_concerns

    @property
    def ingredientsMentioned(self) -> list[str]:
        return self.ingredients_mentioned

    @property
    def excludedIngredients(self) -> list[str]:
        return self.excluded_ingredients

    @property
    def priceMentioned(self) -> float | None:
        return self.price_mentioned

    @property
    def targetUser(self) -> str | None:
        return self.target_user


class QueryConstraints(BaseModel):
    target_type: str = Field(default="PRODUCT")
    category: str | None = Field(default=None)
    brand: str | None = Field(default=None)
    services: list[str] = Field(default_factory=list)
    branch: str | None = Field(default=None)
    skin_type: str | None = Field(default=None)
    min_price: float | None = Field(default=None)
    max_price: float | None = Field(default=None)
    form_factor: str | None = Field(default=None)
    in_stock_only: bool = Field(default=True)
    target_user_safety: str | None = Field(default=None)

    @property
    def target(self) -> str:
        return self.target_type or "PRODUCT"

    @property
    def targetType(self) -> str:
        return self.target_type or "PRODUCT"

    @property
    def skinType(self) -> str | None:
        return self.skin_type

    @property
    def minPrice(self) -> float | None:
        return self.min_price

    @property
    def maxPrice(self) -> float | None:
        return self.max_price

    @property
    def formFactor(self) -> str | None:
        return self.form_factor

    @property
    def inStockOnly(self) -> bool:
        return self.in_stock_only

    @property
    def targetUserSafety(self) -> str | None:
        return self.target_user_safety

    def get(self, key: str, default: Any = None) -> Any:
        return getattr(self, key, default) if hasattr(self, key) else default


class QueryPreferences(BaseModel):
    concerns: list[str] = Field(default_factory=list)
    ingredients: list[str] = Field(default_factory=list)
    target_finish: str | None = Field(default=None)

    @property
    def targetFinish(self) -> str | None:
        return self.target_finish

    def get(self, key: str, default: Any = None) -> Any:
        return getattr(self, key, default) if hasattr(self, key) else default


class QueryNegativePreferences(BaseModel):
    alcohol_free: bool = Field(default=False)
    fragrance_free: bool = Field(default=False)
    excluded_ingredients: list[str] = Field(default_factory=list)
    avoid_texture: str | None = Field(default=None)

    @property
    def alcoholFree(self) -> bool:
        return self.alcohol_free

    @property
    def fragranceFree(self) -> bool:
        return self.fragrance_free

    @property
    def excludedIngredients(self) -> list[str]:
        return self.excluded_ingredients

    @property
    def avoidTexture(self) -> str | None:
        return self.avoid_texture

    def get(self, key: str, default: Any = None) -> Any:
        return getattr(self, key, default) if hasattr(self, key) else default


class DialogueState(BaseModel):
    missing_information: list[str] = Field(default_factory=list)
    needs_clarification: bool = Field(default=False)
    clarification_question: str | None = Field(default=None)

    @property
    def missingInformation(self) -> list[str]:
        return self.missing_information

    @property
    def needsClarification(self) -> bool:
        return self.needs_clarification

    @property
    def clarificationQuestion(self) -> str | None:
        return self.clarification_question


def checkBareOrMissingIntent(rawQuery: str, data: dict | None = None) -> tuple[bool, str, str | None]:
    """
    Kiểm tra xem câu hỏi có phải là dạng danh từ trần trụi hoặc cụm từ chung chung
    chưa có dữ liệu về ý định người dùng (ví dụ: 'serum', 'dịch vụ chăm sóc', 'kem dưỡng') hay không.
    
    Quy tắc:
    - Target: Bắt buộc xác định ("PRODUCT" hoặc "SERVICE"), TUYỆT ĐỐI KHÔNG ĐƯỢC ĐỂ NULL!
    - Intent: Người dùng chưa cung cấp động từ hành vi hoặc thuộc tính điều trị,
      nên Intent chưa có dữ liệu (KHÔNG_XÁC_ĐỊNH) -> Cần kích hoạt hỏi làm rõ!

    Trả về: (is_missing_intent, target_type, detected_name)
    """
    dataDict = data or {}
    q = (rawQuery or str(dataDict.get("raw_query") or "")).lower().strip()
    if not q:
        return True, "PRODUCT", None

    # Kiểm tra hành vi / câu hỏi / mục đích cụ thể
    action_keywords = [
        "tìm", "tư vấn", "gợi ý", "cho mình", "mua", "giá", "bao nhiêu", "nhiêu",
        "đặt lịch", "hẹn lịch", "lịch hẹn", "so sánh", "dùng như thế nào", "cách dùng",
        "có loại nào", "nào tốt", "sao ạ", "khác gì", "dùng được không", "kết hợp",
        "dùng chung", "hỏi về", "bảng giá", "chi phí", "bao nhiêu tiền", "đổi trả",
        "vận chuyển", "đơn hàng", "tại sao", "cơ chế", "thế nào", "có tốt không",
        "phù hợp không", "xem", "chọn", "được không"
    ]
    has_action = any(w in q for w in action_keywords)

    # Kiểm tra đặc tính / vấn đề da / hoạt chất / loại da cụ thể
    detail_keywords = [
        "mụn", "thâm", "nám", "sạm", "kiềm dầu", "mát da", "thấm nhanh", "phục hồi",
        "cấp ẩm", "dưỡng ẩm", "chống lão hóa", "sáng da", "đỏ rát", "cháy nắng",
        "b5", "bha", "aha", "niacinamide", "retinol", "vitamin c", "ha", "ceramide",
        "rau má", "centella", "da dầu", "da nhờn", "da khô", "da nhạy cảm", "da hỗn hợp",
        "không cồn", "không hương liệu", "không nâng tone", "dưới", "triệt lông",
        "lấy mụn", "peel da", "massage", "nặn mụn", "điện di", "vi kim"
    ]
    has_specific_detail = any(w in q for w in detail_keywords)

    # Kiểm tra thương hiệu cụ thể
    brand_keywords = [
        "anessa", "la roche-posay", "la roche posay", "bioderma", "cerave", "simple",
        "klairs", "torriden", "svr", "paula", "3ce", "dhc", "senka", "martiderm"
    ]
    has_brand = any(b in q for b in brand_keywords)

    # Kiểm tra nếu dữ liệu đã có ngữ cảnh tích lũy từ lượt trước (loại da, vấn đề da, hoạt chất, thương hiệu):
    cDict = (dataDict.get("constraints") or {}) if isinstance(dataDict, dict) else {}
    pDict = (dataDict.get("preferences") or {}) if isinstance(dataDict, dict) else {}
    eDict = (dataDict.get("entities") or {}) if isinstance(dataDict, dict) else {}
    has_prior_context = bool(
        cDict.get("skin_type")
        or eDict.get("skin_type")
        or pDict.get("concerns")
        or eDict.get("target_concerns")
        or cDict.get("brand")
        or eDict.get("brands")
        or cDict.get("max_price")
        or pDict.get("ingredients")
    )

    # Nếu câu có hành động, có thuộc tính, có thương hiệu hoặc ĐÃ CÓ NGỮ CẢNH TỪ LƯỢT TRƯỚC: Intent ĐÃ CÓ DỮ LIỆU!
    if has_action or has_specific_detail or has_brand or has_prior_context:
        return False, "PRODUCT", None

    # Nếu câu không có hành vi và không có thuộc tính: Chuẩn hóa chuỗi để nhận diện danh từ trần trụi
    cleaned = re.sub(r'^(ủa|nè|ơi|shop|ad|cho hỏi|alo|hi|hello|cho em hỏi)\s*', '', q)
    cleaned = re.sub(r'\s*(ạ|ơi|nha|nhé|nè|với|đi|ha|nhen|hở|nhở|shop|ad|bên mình|ở đây)$', '', cleaned).strip()

    bare_services = {
        "dịch vụ chăm sóc", "dịch vụ làm đẹp", "chăm sóc da", "dịch vụ spa", "gói chăm sóc",
        "chăm sóc", "spa", "tiệm", "làm đẹp", "gói làm đẹp", "liệu trình làm đẹp", "gói spa"
    }
    if cleaned in bare_services or any(cleaned == s for s in bare_services):
        return True, "SERVICE", cleaned

    bare_products = {
        "serum", "kem", "kem dưỡng", "kem dưỡng da", "kem chống nắng", "sữa rửa mặt",
        "nước tẩy trang", "tẩy trang", "dầu tẩy trang", "toner", "tonner", "nước hoa hồng", "son",
        "son môi", "mặt nạ", "xịt khoáng", "tẩy da chết", "tẩy tế bào chết", "mỹ phẩm",
        "chống nắng", "dưỡng ẩm"
    }
    if cleaned in bare_products or any(cleaned == p for p in bare_products):
        return True, "PRODUCT", cleaned

    # Bỏ qua nếu là lời chào hỏi, cảm ơn hoặc giao tiếp xã giao thuần túy
    greetings = {
        "chào", "xin chào", "hello", "hi", "hey", "alo", "chào shop", "chào bạn",
        "cảm ơn", "cảm ơn shop", "thank", "thanks", "tạm biệt", "bye"
    }
    if cleaned in greetings:
        return False, "PRODUCT", None

    if len(cleaned.split()) <= 3:
        isService = any(w in cleaned for w in ["spa", "dịch vụ", "chăm sóc", "làm đẹp"])
        return True, "SERVICE" if isService else "PRODUCT", cleaned

    return False, "PRODUCT", None


def calculateInformationScore(data: dict, raw_query: str = "") -> float:
    """
    Tính điểm độ đầy đủ thông tin chi tiết (Information Completeness Score).
    Nếu đạt từ 3.0 điểm trở lên, truy vấn đã có đủ dữ kiện để bắt đầu tìm kiếm
    ngay lập tức với lượng thông tin đó mà không cần hỏi thêm người dùng.
    """
    q = (raw_query or str(data.get("raw_query") or "")).lower().strip()

    # Nếu câu truy vấn là danh từ trần trụi chưa rõ ý định (như 'serum', 'dịch vụ chăm sóc'):
    # Phần ý định chưa có dữ liệu -> Điểm độ đầy đủ bằng 0.0 để bắt buộc làm rõ ý định!
    isBare, _, _ = checkBareOrMissingIntent(q, data)
    if isBare:
        return 0.0

    score = 0.0

    # 1. Có thương hiệu hoặc tên sản phẩm cụ thể (+3.5 điểm)
    brand = data.get("brand") or (data.get("entities") or {}).get("brands") or (data.get("entities") or {}).get("brand")
    known_brands = [
        "anessa", "la roche-posay", "la roche posay", "bioderma", "cerave", "simple",
        "klairs", "torriden", "svr", "paula", "3ce", "dhc", "senka", "martiderm",
        "balance", "innisfree", "l'oreal", "eucerin", "centella", "skin1004"
    ]
    if brand or any(b in q for b in known_brands):
        score += 3.5

    # 2. Có danh mục sản phẩm cụ thể (+3.0 điểm)
    cat = data.get("category") or (data.get("entities") or {}).get("product_category") or (data.get("entities") or {}).get("category")
    known_cats = [
        "kem chống nắng", "chống nắng", "sữa rửa mặt", "rửa mặt", "toner", "nước hoa hồng",
        "serum", "tinh chất", "kem dưỡng", "dưỡng ẩm", "tẩy trang", "nước tẩy trang",
        "son môi", "son lì", "mặt nạ", "tẩy tế bào chết", "tẩy da chết", "xịt khoáng",
        "dầu gội", "sữa tắm", "dưỡng thể", "khử mùi", "kem nền", "phấn phủ"
    ]
    if cat or any(c in q for c in known_cats):
        score += 3.0

    # 3. Có dịch vụ Spa / Thẩm mỹ y khoa cụ thể (+4.0 điểm)
    spa_kws = [
        "triệt lông", "peel da", "lấy nhân mụn", "nặn mụn", "massage", "gội đầu dưỡng sinh",
        "điện di", "vi kim", "cấy collagen", "hifu", "nâng cơ", "thải độc da", "soi da",
        "liệu trình trị mụn", "gói chăm sóc da mặt", "chăm sóc da chuyên sâu"
    ]
    if any(s in q for s in spa_kws):
        score += 4.0

    # 4. Có loại da cụ thể (+2.0 điểm)
    cDict = data.get("constraints") or {}
    skin = cDict.get("skin_type") or data.get("skin_type")
    skin_kws = ["da dầu", "da nhờn", "da khô", "da nhạy cảm", "da hỗn hợp", "da mụn", "da thường"]
    if skin or any(s in q for s in skin_kws):
        score += 2.0

    # 5. Có vấn đề về da / Công dụng mong muốn (+2.0 điểm)
    pDict = data.get("preferences") or {}
    concerns = pDict.get("concerns") or pDict.get("concern")
    concern_kws = [
        "mụn", "thâm", "nám", "sạm", "tàn nhang", "kiềm dầu", "đổ nhớt", "cấp ẩm", "phục hồi",
        "lão hóa", "nếp nhăn", "lỗ chân lông", "đỏ rát", "cháy nắng", "mát da", "thấm nhanh",
        "không vón cục", "trắng bệch", "bí da", "lành tính", "dịu da", "kích ứng", "corticoid",
        "kem trộn", "bong tróc", "mỏng đỏ", "lộ chỉ máu"
    ]
    if concerns or any(c in q for c in concern_kws):
        score += 2.0

    # 6. Có thành phần / hoạt chất cụ thể (+2.5 điểm)
    ingrs = pDict.get("ingredients")
    ingr_kws = [
        "b5", "niacinamide", "retinol", "bha", "aha", "vitamin c", "ha", "hyaluronic",
        "panthenol", "ceramide", "salicylic", "benzoyl peroxide", "treti", "tretinoin",
        "adapalene", "rau má", "tràm trà", "centella"
    ]
    if ingrs or any(i in q for i in ingr_kws):
        score += 2.5

    # 7. Có ràng buộc ngân sách / giá tiền (+1.5 điểm)
    price_kws = ["giá", "bao nhiêu", "ngân sách", "tầm giá", "khoảng giá", "tiền", "đồng"]
    if cDict.get("max_price") or cDict.get("price_range") or any(w in q for w in price_kws) or re.search(r'\b\d+\s*(?:k|đ|triệu|nghìn|vnd)\b', q):
        score += 1.5

    # 8. Có tiêu chí phủ định (+2.0 điểm)
    nDict = data.get("negative_preferences") or {}
    if nDict.get("alcohol_free") or nDict.get("fragrance_free") or any(w in q for w in ["không cồn", "không hương liệu", "không nâng tone", "không bết dính"]):
        score += 2.0

    # 9. Có yếu tố an toàn / thai kỳ / treatment (+2.0 điểm)
    targetSafety = cDict.get("target_user_safety")
    hasSafetySpecific = targetSafety and str(targetSafety).lower() not in ("true", "false", "none", "1", "0")
    if hasSafetySpecific or any(w in q for w in ["bầu", "mang thai", "thai kỳ", "sau nặn mụn", "treatment", "đang cho con bú"]):
        score += 2.0

    # 10. Hành vi rõ ràng (Đặt lịch / Bảng giá / Tra cứu tồn kho / So sánh) (+3.0 điểm)
    if any(w in q for w in ["đặt lịch", "bảng giá", "giá bao nhiêu", "còn hàng không", "chi phí", "bao nhiêu tiền"]):
        score += 3.0

    return score


class UnderstandingResult(BaseModel):
    complexity_level: str = Field(default=ComplexityLevel.MUC_1_DON_GIAN.value)
    information_score: float = Field(default=0.0)
    goals: list[str] = Field(default_factory=lambda: [Goal.TIM_SAN_PHAM.value])
    primary_intent: str = Field(default=PrimaryIntent.TU_VAN_SAN_PHAM.value)
    sub_intents: list[str] = Field(default_factory=list)
    target_type: str = Field(default="PRODUCT")
    entities: dict[str, Any] = Field(default_factory=dict)
    constraints: dict[str, Any] = Field(default_factory=dict)
    preferences: dict[str, Any] = Field(default_factory=dict)
    negative_preferences: dict[str, Any] = Field(default_factory=dict)
    dialogue_state: dict[str, Any] = Field(default_factory=dict)
    action_routing: str = Field(default=ActionRouting.CONSTRAINED_HYBRID_SEARCH.value)

    raw_query: str = Field(default="")
    semantic_query: str = Field(default="")
    bm25_query: str = Field(default="")

    intent: Intent = Field(default=Intent.PRODUCT_SEARCH)
    category: str | None = Field(default=None)
    brand: str | None = Field(default=None)
    form_factor: str | None = Field(default=None)
    missing_information: list[str] = Field(default_factory=list)
    needs_clarification: bool = Field(default=False)
    clarification_question: str | None = Field(default=None)
    target_quantity: int | None = Field(default=None)

    @property
    def target(self) -> str:
        return self.target_type or "PRODUCT"

    @property
    def targetType(self) -> str:
        return self.target_type or "PRODUCT"

    @model_validator(mode="before")
    @classmethod
    def normalizeFields(cls, data: Any) -> Any:
        if not isinstance(data, dict):
            return data

        for field in ["constraints", "preferences", "negative_preferences", "entities", "dialogue_state"]:
            val = data.get(field)
            if hasattr(val, "model_dump"):
                data[field] = val.model_dump(exclude_none=True)

        rawQLower = str(data.get("raw_query") or "").lower()

        if not data.get("target_quantity"):
            if any(p in rawQLower for p in ["chỉ 1", "chỉ một", "1 sản phẩm", "một sản phẩm", "duy nhất 1", "duy nhất một", "1 loại", "một loại"]):
                data["target_quantity"] = 1
            elif any(p in rawQLower for p in ["2 sản phẩm", "hai sản phẩm", "2 loại", "hai loại"]):
                data["target_quantity"] = 2
            elif any(p in rawQLower for p in ["3 sản phẩm", "ba sản phẩm", "3 loại", "ba loại"]):
                data["target_quantity"] = 3

        pIntent = data.get("primary_intent")
        legIntent = data.get("intent")

        if pIntent and (not legIntent or legIntent in (Intent.GENERAL_INFORMATION, "GENERAL_INFORMATION")):
            if str(pIntent) in PRIMARY_TO_LEGACY_INTENT:
                data["intent"] = PRIMARY_TO_LEGACY_INTENT[str(pIntent)]
        elif pIntent and not legIntent:
            data["intent"] = PRIMARY_TO_LEGACY_INTENT.get(str(pIntent), Intent.PRODUCT_SEARCH)
        elif legIntent and not pIntent:
            if isinstance(legIntent, Intent):
                data["primary_intent"] = LEGACY_TO_PRIMARY_INTENT.get(legIntent, PrimaryIntent.TU_VAN_SAN_PHAM.value)
            else:
                data["primary_intent"] = LEGACY_TO_PRIMARY_INTENT.get(str(legIntent), PrimaryIntent.TU_VAN_SAN_PHAM.value)
        elif not pIntent and not legIntent:
            data["intent"] = Intent.PRODUCT_SEARCH
            data["primary_intent"] = PrimaryIntent.TU_VAN_SAN_PHAM.value

        if isinstance(data.get("intent"), str):
            try:
                data["intent"] = Intent(data["intent"])
            except ValueError:
                data["intent"] = PRIMARY_TO_LEGACY_INTENT.get(data["intent"], Intent.PRODUCT_SEARCH)

        dialogueStateDict = data.get("dialogue_state")
        if isinstance(dialogueStateDict, dict):
            if "needs_clarification" in dialogueStateDict and "needs_clarification" not in data:
                data["needs_clarification"] = dialogueStateDict["needs_clarification"]
            if "clarification_question" in dialogueStateDict and "clarification_question" not in data:
                data["clarification_question"] = dialogueStateDict["clarification_question"]
            if "missing_information" in dialogueStateDict and "missing_information" not in data:
                data["missing_information"] = dialogueStateDict.get("missing_information", [])
        else:
            data["dialogue_state"] = {
                "needs_clarification": data.get("needs_clarification", False),
                "clarification_question": data.get("clarification_question"),
                "missing_information": data.get("missing_information", []),
            }

        entitiesDict = data.get("entities")
        if isinstance(entitiesDict, dict):
            if entitiesDict.get("product_category") and not data.get("category"):
                data["category"] = entitiesDict["product_category"]
            if entitiesDict.get("form_factor") and not data.get("form_factor"):
                data["form_factor"] = entitiesDict["form_factor"]
            brands = entitiesDict.get("brands") or []
            if brands and not data.get("brand"):
                data["brand"] = brands[0]
        else:
            entDict: dict[str, Any] = {}
            if data.get("category"):
                entDict["product_category"] = data["category"]
            if data.get("brand"):
                entDict["brands"] = [data["brand"]]
            if data.get("form_factor"):
                entDict["form_factor"] = data["form_factor"]
            data["entities"] = entDict

        formFactorVal = data.get("form_factor")
        if formFactorVal and isinstance(data.get("constraints"), dict) and "form_factor" not in data["constraints"]:
            data["constraints"]["form_factor"] = formFactorVal

        curP = data.get("primary_intent")
        curPVal = curP.value if hasattr(curP, "value") else str(curP or "")
        entitiesDict = data.get("entities")
        if not isinstance(data.get("constraints"), dict):
            data["constraints"] = {}

        # 1. XỬ LÝ TRUY VẤN DANH TỪ TRẦN TRỤI CHƯA RÕ Ý ĐỊNH (Bare / Missing Intent)
        isBare, detectedTarget, bareName = checkBareOrMissingIntent(rawQLower, data)
        if isBare:
            targetTypeVal = detectedTarget or "PRODUCT"
            data["target_type"] = targetTypeVal
            data["target"] = targetTypeVal
            if isinstance(entitiesDict, dict):
                entitiesDict["target_type"] = targetTypeVal
            if isinstance(data.get("constraints"), dict):
                data["constraints"]["target_type"] = targetTypeVal

            data["primary_intent"] = PrimaryIntent.KHONG_XAC_DINH.value
            data["intent"] = Intent.UNKNOWN
            data["needs_clarification"] = True
            data["action_routing"] = ActionRouting.CLARIFICATION_PROMPT.value
            data["information_score"] = 0.0

            if not isinstance(data.get("dialogue_state"), dict):
                data["dialogue_state"] = {}
            data["dialogue_state"]["needs_clarification"] = True
            data["dialogue_state"]["missing_information"] = ["primary_intent", "specific_need"]
            data["missing_information"] = ["primary_intent", "specific_need"]

            if targetTypeVal == "SERVICE":
                data["clarification_question"] = (
                    "Dạ, viện Beauty Spa & Clinic hiện có các nhóm dịch vụ: Chăm sóc & Điều trị da mặt y khoa "
                    "(lấy mụn, peel da, phục hồi B5), Massage thư giãn body và Triệt lông công nghệ cao. "
                    "Bạn đang quan tâm đến nhóm dịch vụ nào hoặc muốn tham khảo bảng giá chi tiết / đặt lịch hẹn trước ạ?"
                )
            else:
                catName = bareName or data.get("category") or "sản phẩm"
                data["clarification_question"] = (
                    f"Dạ, bạn đang cần tìm {catName} cho loại da nào (da dầu, da khô hay nhạy cảm) "
                    "và mục đích sử dụng là gì (cấp ẩm, phục hồi, trị mụn hay dưỡng sáng) "
                    "để em tư vấn sản phẩm thích hợp nhất cho bạn ạ?"
                )
            data["dialogue_state"]["clarification_question"] = data["clarification_question"]
            return data

        # 2. XÁC ĐỊNH TARGET TYPE (TARGET TUYỆT ĐỐI KHÔNG ĐƯỢC PHÉP ĐỂ NULL!)
        targetTypeVal = None
        if isinstance(entitiesDict, dict) and entitiesDict.get("target_type"):
            targetTypeVal = str(entitiesDict["target_type"]).upper()
        elif isinstance(data.get("constraints"), dict) and data["constraints"].get("target_type"):
            targetTypeVal = str(data["constraints"]["target_type"]).upper()
        elif data.get("target_type"):
            targetTypeVal = str(data["target_type"]).upper()
        elif data.get("target"):
            targetTypeVal = str(data["target"]).upper()

        spaKeywords = (
            "triệt lông", "peel da", "massage", "lấy nhân mụn", "nặn mụn", "soi da",
            "điện di", "vi kim", "cấy collagen", "hifu", "nâng cơ", "thải độc da",
            "dịch vụ chăm sóc", "dịch vụ làm đẹp", "chăm sóc da", "spa", "gói chăm sóc",
            "mần đẹp", "tiệm", "liệu trình", "đặt lịch", "hẹn lịch"
        )
        if any(k in rawQLower for k in spaKeywords):
            if not targetTypeVal or targetTypeVal not in ("SERVICE", "COMBO"):
                hasComboIndicator = "combo" in rawQLower or "kèm" in rawQLower or "cùng với" in rawQLower
                targetTypeVal = "COMBO" if hasComboIndicator else "SERVICE"

        if not targetTypeVal or targetTypeVal in ("NONE", "NULL", ""):
            if curPVal in (
                PrimaryIntent.DICH_VU_LAM_DEP.value,
                PrimaryIntent.DAT_LICH.value,
                "DỊCH_VỤ_LÀM_ĐẸP",
                "ĐẶT_LỊCH",
            ) or (isinstance(entitiesDict, dict) and bool(entitiesDict.get("services"))):
                targetTypeVal = "SERVICE"
            else:
                targetTypeVal = "PRODUCT"

        hasComboIndicator = "combo" in rawQLower or "kèm" in rawQLower or "cùng với" in rawQLower
        if targetTypeVal == "COMBO" and not hasComboIndicator:
            hasBothEntities = isinstance(entitiesDict, dict) and bool(entitiesDict.get("product_category")) and bool(entitiesDict.get("services"))
            if not hasBothEntities:
                targetTypeVal = "SERVICE" if (isinstance(entitiesDict, dict) and bool(entitiesDict.get("services"))) else "PRODUCT"

        # ĐẢM BẢO TARGET TYPE LUÔN CÓ GIÁ TRỊ CỤ THỂ KHÔNG NULL
        targetTypeVal = targetTypeVal or "PRODUCT"
        data["target_type"] = targetTypeVal
        data["target"] = targetTypeVal
        if isinstance(entitiesDict, dict):
            entitiesDict["target_type"] = targetTypeVal
        if isinstance(data.get("constraints"), dict):
            data["constraints"]["target_type"] = targetTypeVal

        if targetTypeVal == "SERVICE":
            data["category"] = None
            data["brand"] = None
            if "category" in data["constraints"]:
                data["constraints"]["category"] = None
            if "brand" in data["constraints"]:
                data["constraints"]["brand"] = None
            if not data.get("intent"):
                data["intent"] = Intent.PRODUCT_SEARCH

        if isinstance(entitiesDict, dict):
            if entitiesDict.get("services") and "services" not in data["constraints"]:
                data["constraints"]["services"] = entitiesDict["services"]
            if entitiesDict.get("branch") and "branch" not in data["constraints"]:
                data["constraints"]["branch"] = entitiesDict["branch"]

            targetUser = entitiesDict.get("target_user")
            if targetUser and any(w in str(targetUser).lower() for w in ("bầu", "mang thai", "pregnant")):
                if not data["constraints"].get("target_user_safety"):
                    data["constraints"]["target_user_safety"] = "pregnant_safe"

        if not data.get("goals"):
            if curP in (PrimaryIntent.GIA_NGAN_SACH.value, PrimaryIntent.THONG_TIN_SAN_PHAM.value, PrimaryIntent.HOI_DAP_SAN_PHAM.value):
                data["goals"] = [Goal.TIM_THONG_TIN.value]
            elif curP in (PrimaryIntent.SO_SANH_SAN_PHAM.value,):
                data["goals"] = [Goal.SO_SANH.value]
            elif curP in (PrimaryIntent.XAY_DUNG_CHU_TRINH.value,):
                data["goals"] = [Goal.XAY_DUNG.value]
            elif curP in (PrimaryIntent.PHAN_TICH_CHU_TRINH.value,):
                data["goals"] = [Goal.DANH_GIA.value]
            elif curP in (PrimaryIntent.DAT_LICH.value,):
                data["goals"] = [Goal.DAT_LICH.value]
            else:
                data["goals"] = [Goal.TIM_SAN_PHAM.value]

        # TÍNH ĐIỂM ĐỘ ĐẦY ĐỦ THÔNG TIN CHI TIẾT (INFORMATION COMPLETENESS SCORE)
        infoScore = calculateInformationScore(data, rawQLower)
        data["information_score"] = infoScore

        # THRESHOLD RULES:
        # Điều kiện tiên quyết: ĐÃ CÓ Ý ĐỊNH RÕ RÀNG (không phải KHÔNG_XÁC_ĐỊNH, không phải UNKNOWN)
        # VÀ các trường thông tin chi tiết (danh mục, thương hiệu, vấn đề da, hoạt chất...) đạt >= 3.0 điểm:
        # => TIẾN HÀNH TÌM KIẾM NGAY với lượng thông tin hiện có, KHÔNG HỎI THÊM!
        hasValidIntent = (
            curPVal not in (PrimaryIntent.KHONG_XAC_DINH.value, "KHÔNG_XÁC_ĐỊNH", "KHONG_XAC_DINH", "")
            and data.get("intent") not in (Intent.UNKNOWN, "UNKNOWN")
        )

        isGreeting = curPVal in (PrimaryIntent.CHAO_HOI.value, "CHÀO_HỎI", "CHAO_HOI") or data.get("intent") in (Intent.GENERAL_CHAT, "GENERAL_CHAT")

        if isGreeting:
            data["needs_clarification"] = False
            data["action_routing"] = ActionRouting.FAST_PATH.value
            if isinstance(data.get("dialogue_state"), dict):
                data["dialogue_state"]["needs_clarification"] = False
                data["dialogue_state"]["clarification_question"] = None
        elif infoScore >= 3.0 and hasValidIntent:
            data["needs_clarification"] = False
            if str(data.get("action_routing")) == "CLARIFICATION_PROMPT":
                data["action_routing"] = ActionRouting.CONSTRAINED_HYBRID_SEARCH.value
            if isinstance(data.get("dialogue_state"), dict):
                data["dialogue_state"]["needs_clarification"] = False
                data["dialogue_state"]["clarification_question"] = None
        else:
            # Dưới ngưỡng hoặc chưa rõ ý định: Bắt buộc hỏi làm rõ
            needsClarify = bool(data.get("needs_clarification"))
            dialogueStateDict = data.get("dialogue_state")
            if isinstance(dialogueStateDict, dict) and dialogueStateDict.get("needs_clarification"):
                needsClarify = True
            if str(data.get("action_routing")) == "CLARIFICATION_PROMPT" or not hasValidIntent:
                needsClarify = True

            if needsClarify:
                data["needs_clarification"] = True
                data["action_routing"] = "CLARIFICATION_PROMPT"

                if not data.get("clarification_question"):
                    if targetTypeVal == "SERVICE":
                        data["clarification_question"] = (
                            "Dạ, viện Beauty Spa & Clinic hiện có các nhóm dịch vụ: Chăm sóc & Điều trị da mặt y khoa "
                            "(lấy mụn, peel da, phục hồi B5), Massage thư giãn body và Triệt lông công nghệ cao. "
                            "Bạn đang quan tâm đến nhóm dịch vụ nào hoặc muốn tham khảo bảng giá chi tiết / đặt lịch hẹn trước ạ?"
                        )
                    else:
                        data["clarification_question"] = (
                            "Dạ, để em tư vấn sản phẩm phù hợp nhất với làn da của bạn, bạn có thể chia sẻ thêm về "
                            "loại da của mình (da dầu, da khô hay nhạy cảm) và vấn đề da bạn đang muốn cải thiện được không ạ?"
                        )

                if not isinstance(data.get("dialogue_state"), dict):
                    data["dialogue_state"] = {}
                data["dialogue_state"]["needs_clarification"] = True
                data["dialogue_state"]["clarification_question"] = data.get("clarification_question")

        if "rawMessage" in data and "raw_query" not in data:
            data["raw_query"] = data["rawMessage"]
        elif "raw_message" in data and "raw_query" not in data:
            data["raw_query"] = data["raw_message"]

        return data

    normalize_fields = normalizeFields

    @property
    def rawMessage(self) -> str:
        return self.raw_query

    @property
    def raw_message(self) -> str:
        return self.raw_query

    @property
    def rawQuery(self) -> str:
        return self.raw_query

    @property
    def semanticQuery(self) -> str:
        return self.semantic_query

    @property
    def bm25Query(self) -> str:
        return self.bm25_query

    @property
    def complexityLevel(self) -> str:
        return self.complexity_level

    @property
    def primaryIntent(self) -> str:
        return self.primary_intent

    @property
    def subIntents(self) -> list[str]:
        return self.sub_intents

    @property
    def negativePreferences(self) -> dict[str, Any]:
        return self.negative_preferences

    @property
    def dialogueState(self) -> dict[str, Any]:
        return self.dialogue_state

    @property
    def actionRouting(self) -> str:
        return self.action_routing

    @property
    def formFactor(self) -> str | None:
        return self.form_factor

    @property
    def missingInformation(self) -> list[str]:
        return self.missing_information

    @property
    def needsClarification(self) -> bool:
        return self.needs_clarification

    @property
    def clarificationQuestion(self) -> str | None:
        return self.clarification_question

    @property
    def targetQuantity(self) -> int | None:
        return self.target_quantity
