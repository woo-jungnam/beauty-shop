import re
from typing import Any, Dict, Optional, Tuple
from app.core.taxonomy import Taxonomy
from app.repositories.order_repository import OrderRepository, orderRepository
from app.schemas.query import (
    ActionRouting,
    ComplexityLevel,
    Goal,
    Intent,
    PrimaryIntent,
    UnderstandingResult,
    calculateInformationScore,
)
from app.services.ingestion.cleaning import TextCleaner


class QueryRouter:
    ORDER_NUMBER_PATTERN = OrderRepository.ORDER_NUMBER_PATTERN
    PHONE_PATTERN = OrderRepository.PHONE_PATTERN

    RETRIEVAL_INTENTS = {
        Intent.PRODUCT_SEARCH,
        Intent.PRODUCT_DETAIL,
        Intent.COMPARISON,
        Intent.PRICE_QUERY,
        Intent.AVAILABILITY_QUERY,
        Intent.ROUTINE_RECOMMENDATION,
        Intent.BOOKING,
        "RECOMMENDATION",
        "ROUTINE_RECOMMENDATION",
        "BOOKING",
    }

    CANNED_RESPONSES = {
        Intent.ORDER_STATUS: (
            "Để tra cứu đơn hàng, bạn vui lòng cung cấp đồng thời Mã đơn hàng "
            "(ORD- kèm 32 ký tự chữ số hệ 16 trên xác nhận đặt hàng) và số điện thoại đặt hàng "
            "trong cùng một tin nhắn. Số điện thoại phải gồm 10 chữ số bắt đầu bằng 03, 05, 07, 08, 09 "
            "hoặc dùng đầu +84 tương ứng."
        ),
        Intent.GENERAL_CHAT: (
            "Xin chào! Tôi là trợ lý AI chuyên về mỹ phẩm và dịch vụ chăm sóc da tại BeautyShop. "
            "Bạn đang quan tâm đến sản phẩm chăm sóc da nào hay cần hỗ trợ gì ạ?"
        ),
        Intent.OUT_OF_DOMAIN: (
            "Dạ, tôi là trợ lý AI chuyên về tư vấn mỹ phẩm và dịch vụ làm đẹp tại spa. "
            "Câu hỏi của bạn hiện nằm ngoài phạm vi chuyên môn của tôi. "
            "Bạn có thể hỏi tôi về sản phẩm dưỡng da hoặc đặt lịch dịch vụ spa nhé!"
        ),
    }

    def shouldRetrieve(self, understanding: UnderstandingResult) -> bool:
        pIntent = getattr(understanding.primary_intent, "value", str(understanding.primary_intent or ""))
        uIntent = getattr(understanding.intent, "value", str(understanding.intent or ""))

        if pIntent in (PrimaryIntent.NGOAI_PHAM_VI.value, "NGOÀI_PHẠM_VI", "NGOAI_PHAM_VI") or understanding.intent == Intent.OUT_OF_DOMAIN:
            return False

        if understanding.intent == Intent.ORDER_STATUS or pIntent in (
            PrimaryIntent.HO_TRO_DON_HANG.value,
            "HỖ_TRỢ_ĐƠN_HÀNG",
            "HO_TRO_DON_HANG",
        ):
            return False

        if uIntent == "CONSULTATION" or understanding.intent == Intent.CONSULTATION:
            return False

        if understanding.needs_clarification:
            return False

        hasValidIntent = (
            pIntent not in (PrimaryIntent.KHONG_XAC_DINH.value, "KHÔNG_XÁC_ĐỊNH", "KHONG_XAC_DINH", "")
            and understanding.intent not in (Intent.UNKNOWN, "UNKNOWN")
        )
        if hasValidIntent and getattr(understanding, "information_score", 0.0) >= 3.0:
            understanding.needs_clarification = False

        if getattr(understanding, "action_routing", None) in (
            ActionRouting.CHAIN_OF_THOUGHT_REASONING,
            ActionRouting.CONSTRAINED_HYBRID_SEARCH,
            ActionRouting.DIRECT_DATABASE_LOOKUP,
            "CHAIN_OF_THOUGHT_REASONING",
            "CONSTRAINED_HYBRID_SEARCH",
            "DIRECT_DATABASE_LOOKUP",
        ):
            return True

        pIntent = getattr(understanding.primary_intent, "value", str(understanding.primary_intent or ""))
        clinicalRetrievalPrimaryIntents = {
            PrimaryIntent.TUONG_THICH_THANH_PHAN.value,
            PrimaryIntent.PHAN_TICH_CHU_TRINH.value,
            PrimaryIntent.XAY_DUNG_CHU_TRINH.value,
            PrimaryIntent.TAC_DUNG_THANH_PHAN.value,
            PrimaryIntent.THONG_TIN_THANH_PHAN.value,
            PrimaryIntent.TU_VAN_SAN_PHAM.value,
            PrimaryIntent.THONG_TIN_SAN_PHAM.value,
            PrimaryIntent.SO_SANH_SAN_PHAM.value,
            PrimaryIntent.DICH_VU_LAM_DEP.value,
            PrimaryIntent.DAT_LICH.value,
            PrimaryIntent.VAN_DE_DA.value,
            PrimaryIntent.PHU_HUB_LAN_DA.value if hasattr(PrimaryIntent, "PHU_HUB_LAN_DA") else getattr(PrimaryIntent, "PHU_HOP_LAN_DA", None) and PrimaryIntent.PHU_HOP_LAN_DA.value,
        }
        if pIntent in clinicalRetrievalPrimaryIntents:
            return True

        return (
            understanding.intent in self.RETRIEVAL_INTENTS
            or str(understanding.intent) in self.RETRIEVAL_INTENTS
        )

    should_retrieve = shouldRetrieve

    def handleOrderLookup(self, rawText: str) -> Tuple[str, Optional[Dict[str, Any]]]:
        cleanText = rawText.strip()
        orderNumbers = {match.upper() for match in self.ORDER_NUMBER_PATTERN.findall(cleanText)}
        # Remove order tokens before parsing phones; a numeric code is not a second proof.
        phoneText = re.sub(r"(?<!\w)#?ORD-[\w-]+", " ", cleanText, flags=re.I)
        phones = {
            "0" + phone[3:] if phone.startswith("+84") else phone
            for phone in self.PHONE_PATTERN.findall(phoneText)
        }
        if len(orderNumbers) != 1 or len(phones) != 1:
            return self.CANNED_RESPONSES[Intent.ORDER_STATUS], None

        order = orderRepository.findOrder(next(iter(orderNumbers)), next(iter(phones)))
        if order:
            return orderRepository.formatOrderSummary(order), order
        return (
            "Không tìm thấy đơn hàng khớp với mã đơn và số điện thoại đặt hàng. "
            "Bạn vui lòng kiểm tra lại cả hai thông tin.",
            None,
        )

    handle_order_lookup = handleOrderLookup

    def handleNonRetrieval(
        self,
        understanding: UnderstandingResult,
        userMessage: str = "",
        **kwargs: Any,
    ) -> Optional[str]:
        uMsg = userMessage or kwargs.get("user_message", "")
        intent = understanding.intent
        pIntent = getattr(understanding.primary_intent, "value", str(understanding.primary_intent or ""))
        if intent == Intent.OUT_OF_DOMAIN or pIntent in (PrimaryIntent.NGOAI_PHAM_VI.value, "NGOÀI_PHẠM_VI", "NGOAI_PHAM_VI"):
            return self.CANNED_RESPONSES[Intent.OUT_OF_DOMAIN]

        if intent == Intent.ORDER_STATUS or pIntent in (
            PrimaryIntent.HO_TRO_DON_HANG.value,
            "HỖ_TRỢ_ĐƠN_HÀNG",
            "HO_TRO_DON_HANG",
        ):
            reply, _ = self.handleOrderLookup(uMsg)
            return reply

        if understanding.needs_clarification and understanding.clarification_question:
            return understanding.clarification_question
        return self.CANNED_RESPONSES.get(intent) or (
            self.CANNED_RESPONSES[Intent.GENERAL_CHAT]
            if intent in (Intent.GENERAL_INFORMATION, Intent.UNKNOWN, "UNKNOWN")
            else None
        )

    handle_non_retrieval = handleNonRetrieval


class FastPathParser:
    THANKS_PATTERN = re.compile(r"^(cảm ơn|cam on|thanks|thank you|ok shop|oke shop|oki shop|dạ cảm ơn)", re.I)
    GREETING_PATTERN = re.compile(
        r"^(chào(\s+shop|\s+bạn|\s+ad)?(\s+buổi\s+(sáng|trưa|chiều|tối))?(\s+nhé|\s+nha|\s+nghen|\s+ơi|\s+ạ)?|"
        r"hello(\s+shop|\s+ad)?(\s+nhé|\s+nha|\s+ơi|\s+ạ)?|"
        r"hi(\s+shop|\s+ad)?(\s+nhé|\s+nha|\s+ơi|\s+ạ)?|"
        r"alo(\s+shop|\s+ad)?(\s+nhé|\s+nha|\s+ơi|\s+ạ)?|"
        r"(shop|ad)\s+ơi)$",
        re.I,
    )
    ORDER_STATUS_PATTERN = re.compile(
        r"(tra cứu|kiểm tra|check|tình trạng|tiến độ|trạng thái)\s+(đơn hàng|don hang|đơn|don)\b|"
        r"\b(mã đơn|ma don|mã vận đơn)\b|"
        r"^#(?:ord|dh)?[a-z0-9_-]{4,30}$|"
        r"^(?:ord|dh)-[a-z0-9_-]{4,25}$",
        re.I,
    )
    OUT_OF_DOMAIN_PATTERNS = (
        re.compile(r"^\s*(\d+\s*[\+\-\*\/xX\=]\s*[\d\s\+\-\*\/xX\=\?]*|[\d\s\+\-\*\/xX\=\?]+\=[\d\s\+\-\*\/xX\=\?]*)\s*$", re.I),
        re.compile(r"(\d+\s*(cộng|trừ|nhân|chia)\s*\d+|bằng\s+mấy|bằng\s+bao\s+nhiêu\s*(\?|$))", re.I),
        re.compile(r"(thời tiết|trời mưa|mưa không|trời nắng|mấy giờ|bây giờ là mấy giờ|hôm nay thứ mấy|viết code|lập trình|dịch bài|kể chuyện|hát đi)", re.I),
    )
    PRICE_KEYWORDS = ("dưới", "tầm", "khoảng", "rẻ hơn", "ngân sách", "tiền", "<=")
    CATEGORY_PREFIXES = ("đổi sang ", "chuyển sang ", "sang ", "qua ", "loại ", "chọn ")

    def tryParse(self, message: str, ctx) -> Optional[UnderstandingResult]:
        cleanPunct = re.sub(r"[^\w\s]", " ", message.strip().lower())
        clean = " ".join(cleanPunct.split())
        if not clean:
            clean = message.strip().lower()

        if (
            self.ORDER_STATUS_PATTERN.search(message)
            or self.ORDER_STATUS_PATTERN.search(clean)
            or QueryRouter.ORDER_NUMBER_PATTERN.search(message)
            or OrderRepository.PHONE_PATTERN.fullmatch(message.strip())
        ):
            res = self.buildResult(Intent.ORDER_STATUS, "tra cứu đơn hàng", "tra cứu đơn hàng")
            res.primary_intent = PrimaryIntent.HO_TRO_DON_HANG.value
            res.action_routing = ActionRouting.FAST_PATH.value
            res.needs_clarification = False
            return res

        for pat in self.OUT_OF_DOMAIN_PATTERNS:
            if pat.search(clean) or pat.search(message):
                res = self.buildResult(Intent.OUT_OF_DOMAIN, message, message)
                res.primary_intent = PrimaryIntent.NGOAI_PHAM_VI.value
                res.action_routing = ActionRouting.FAST_PATH.value
                res.needs_clarification = False
                return res

        if self.THANKS_PATTERN.search(clean):
            return self.buildResult(Intent.GENERAL_INFORMATION, message, message)

        if self.GREETING_PATTERN.match(clean):
            res = self.buildResult(Intent.GENERAL_CHAT, message, message)
            res.primary_intent = "CHÀO_HỎI"
            res.action_routing = ActionRouting.FAST_PATH.value
            res.needs_clarification = False
            return res

        if self.THANKS_PATTERN.search(clean):
            return self.buildResult(Intent.GENERAL_INFORMATION, message, message)

        if self.GREETING_PATTERN.match(clean):
            res = self.buildResult(Intent.GENERAL_CHAT, message, message)
            res.primary_intent = "CHÀO_HỎI"
            res.action_routing = ActionRouting.FAST_PATH.value
            res.needs_clarification = False
            return res

        complexPatterns = ("so sánh", "so sanh", "vs", "hay", "tốt hơn", "khác nhau", "nên chọn", "routine", "combo", "chu trình", "dùng chung", "kết hợp", "thế nào", "như thế nào")
        if any(w in clean for w in complexPatterns):
            return None

        accConstraints = getattr(ctx, "accumulatedConstraints", None) or getattr(ctx, "accumulated_constraints", {})
        accPrefs = getattr(ctx, "accumulatedPreferences", None) or getattr(ctx, "accumulated_preferences", {})
        if str(accConstraints.get("target_type", "")).upper() == "SERVICE":
            return None

        accCat = getattr(ctx, "accumulatedCategory", None) or getattr(ctx, "accumulated_category", None)
        if not (accCat or accConstraints or accPrefs):
            return None

        return self.matchPrice(clean, message, ctx) or self.matchCategory(clean, message, ctx)

    try_parse = tryParse

    def matchPrice(self, clean: str, raw: str, ctx) -> Optional[UnderstandingResult]:
        if not any(k in clean for k in self.PRICE_KEYWORDS) or len(clean.split()) > 10:
            return None
        if any(w in clean for w in ("tìm sản phẩm khác", "chuyển sang danh mục")):
            return None

        val = TextCleaner.parsePrice(clean)
        if not val or val <= 10_000:
            return None

        accCat = getattr(ctx, "accumulatedCategory", None) or getattr(ctx, "accumulated_category", None)
        cat = accCat or "sản phẩm"
        return self.buildResult(
            intent=Intent.PRODUCT_SEARCH,
            raw=raw,
            semantic=f"{cat} dưới {val} VND",
            constraints={"max_price": val},
        )

    _match_price = matchPrice

    def matchCategory(self, clean: str, raw: str, ctx=None) -> Optional[UnderstandingResult]:
        target = clean
        hasPrefix = False
        for prefix in self.CATEGORY_PREFIXES:
            if target.startswith(prefix):
                target = target[len(prefix):].strip()
                hasPrefix = True
                break

        normCat = Taxonomy.normalizeCategory(target)
        if not normCat or Taxonomy.getDomain(normCat) is None:
            return None

        accConstraints = getattr(ctx, "accumulatedConstraints", {}) if ctx else {}
        accPrefs = getattr(ctx, "accumulatedPreferences", {}) if ctx else {}
        hasContext = bool(accConstraints.get("skin_type") or accPrefs.get("concerns"))

        if (hasPrefix or hasContext) and len(target.split()) <= 3:
            skin = accConstraints.get("skin_type", "")
            concerns = accPrefs.get("concerns", [])
            concernStr = " ".join(concerns) if isinstance(concerns, list) else str(concerns or "")
            semanticStr = f"{normCat} cho {skin} {concernStr}".strip()

            mergedC = dict(accConstraints)
            mergedC["category"] = normCat

            res = self.buildResult(
                intent=Intent.PRODUCT_SEARCH,
                raw=raw,
                semantic=semanticStr or f"{normCat} {clean}",
                category=normCat,
                constraints=mergedC,
            )
            res.primary_intent = PrimaryIntent.TU_VAN_SAN_PHAM.value
            res.preferences = dict(accPrefs)
            res.information_score = calculateInformationScore(res.model_dump(), raw)
            res.needs_clarification = False
            res.action_routing = ActionRouting.CONSTRAINED_HYBRID_SEARCH.value
            return res
        return None

    _match_category = matchCategory

    @staticmethod
    def buildResult(
        intent: Intent,
        raw: str,
        semantic: str,
        category: Optional[str] = None,
        constraints: Optional[dict] = None,
    ) -> UnderstandingResult:
        isInfo = intent in (Intent.GENERAL_INFORMATION, Intent.PRICE_QUERY)
        return UnderstandingResult(
            complexity_level=ComplexityLevel.MUC_1_DON_GIAN.value,
            goals=[Goal.TIM_THONG_TIN.value] if isInfo else [Goal.TIM_SAN_PHAM.value],
            action_routing=ActionRouting.FAST_PATH.value,
            intent=intent,
            category=category,
            constraints=constraints or {},
            preferences={},
            negative_preferences={},
            needs_clarification=False,
            raw_query=raw,
            semantic_query=semantic,
            bm25_query=semantic,
        )

    _build_result = buildResult


queryRouter = QueryRouter()
query_router = queryRouter

fastPathParser = FastPathParser()
fast_path_parser = fastPathParser
