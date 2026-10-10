import re
from typing import Optional
from app.core.taxonomy import Taxonomy
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
    RETRIEVAL_INTENTS = {
        Intent.PRODUCT_SEARCH,
        Intent.PRODUCT_DETAIL,
        Intent.COMPARISON,
        Intent.PRICE_QUERY,
        Intent.AVAILABILITY_QUERY,
        Intent.ROUTINE_RECOMMENDATION,
        "RECOMMENDATION",
        "ROUTINE_RECOMMENDATION",
    }

    CANNED_RESPONSES = {
        Intent.ORDER_STATUS: (
            "Để tra cứu đơn hàng, bạn vui lòng cung cấp Mã đơn hàng (ví dụ: #DH12345) "
            "hoặc số điện thoại đặt hàng để hệ thống hỗ trợ kiểm tra tiến độ giao hàng nhé!"
        ),
        Intent.GENERAL_CHAT: (
            "Xin chào! Tôi là chuyên gia tư vấn mỹ phẩm và chăm sóc da cá nhân hóa. "
            "Tôi có thể hỗ trợ bạn tìm kiếm sản phẩm tối ưu theo từng loại da (da dầu, da khô, da nhạy cảm...), "
            "vấn đề da (mụn, thâm, lão hóa, phục hồi) và mức ngân sách mong muốn. "
            "Hôm nay bạn cần tìm sản phẩm chăm sóc da nào ạ?"
        ),
    }

    def shouldRetrieve(self, understanding: UnderstandingResult) -> bool:
        pIntent = getattr(understanding.primary_intent, "value", str(understanding.primary_intent or ""))
        hasValidIntent = (
            pIntent not in (PrimaryIntent.KHONG_XAC_DINH.value, "KHÔNG_XÁC_ĐỊNH", "KHONG_XAC_DINH", "")
            and understanding.intent not in (Intent.UNKNOWN, "UNKNOWN")
        )
        if hasValidIntent and getattr(understanding, "information_score", 0.0) >= 3.0:
            understanding.needs_clarification = False

        if understanding.needs_clarification:
            return False

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

    def handleNonRetrieval(self, understanding: UnderstandingResult) -> Optional[str]:
        if understanding.needs_clarification and understanding.clarification_question:
            return understanding.clarification_question
        intent = understanding.intent
        return self.CANNED_RESPONSES.get(intent) or (
            self.CANNED_RESPONSES[Intent.GENERAL_CHAT]
            if intent in (Intent.GENERAL_INFORMATION, Intent.UNKNOWN, "UNKNOWN")
            else None
        )

    handle_non_retrieval = handleNonRetrieval


class FastPathParser:
    THANKS_PATTERN = re.compile(r"^(cảm ơn|cam on|thanks|thank you|ok shop|oke shop|oki shop|dạ cảm ơn)", re.I)
    GREETING_PATTERN = re.compile(
        r"^(chào(\s+shop|\s+bạn|\s+ad)?(\s+buổi\s+(sáng|trưa|chiều|tối))?(\s+nhé|\s+nha|\s+nghen)?|"
        r"hello(\s+shop)?(\s+nhé|\s+nha)?|"
        r"hi(\s+shop)?(\s+nhé|\s+nha)?|"
        r"alo(\s+shop)?(\s+nhé|\s+nha)?)$",
        re.I,
    )
    PRICE_KEYWORDS = ("dưới", "tầm", "khoảng", "rẻ hơn", "ngân sách", "tiền", "<=")
    CATEGORY_PREFIXES = ("đổi sang ", "chuyển sang ", "sang ", "qua ", "loại ", "chọn ")

    def tryParse(self, message: str, ctx) -> Optional[UnderstandingResult]:
        clean = message.strip().lower()
        if self.THANKS_PATTERN.search(clean):
            return self.buildResult(Intent.GENERAL_INFORMATION, message, message)

        if self.GREETING_PATTERN.match(clean):
            res = self.buildResult(Intent.GENERAL_CHAT, message, message)
            res.primary_intent = "CHÀO_HỎI"
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
