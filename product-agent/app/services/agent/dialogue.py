import time
from abc import ABC, abstractmethod
from typing import Any, Dict, List, Optional
from pydantic import BaseModel, ConfigDict, Field

from app.core.taxonomy import Taxonomy
from app.schemas.query import (
    ActionRouting,
    Intent,
    PrimaryIntent,
    UnderstandingResult,
    calculateInformationScore,
)
from app.services.agent.memory import EntityDialogueMemory, TrackedProductEntity


class BaseSessionStore(ABC):

    @abstractmethod
    def get(self, sessionId: str) -> Optional["ConversationContext"]:
        pass

    @abstractmethod
    def set(self, sessionId: str, context: "ConversationContext") -> None:
        pass

    @abstractmethod
    def delete(self, sessionId: str) -> None:
        pass

    @abstractmethod
    def cleanupExpired(self) -> None:
        pass

    cleanup_expired = cleanupExpired


class InMemorySessionStore(BaseSessionStore):

    def __init__(self, ttlSeconds: int = 3600, **kwargs: Any):
        self.ttlSeconds = ttlSeconds if "ttlSeconds" in kwargs or ttlSeconds != 3600 else kwargs.get("ttl_seconds", ttlSeconds)
        self.ttl_seconds = self.ttlSeconds
        self._sessions: Dict[str, "ConversationContext"] = {}

    def get(self, sessionId: str) -> Optional["ConversationContext"]:
        self.cleanupExpired()
        ctx = self._sessions.get(sessionId)
        if ctx is not None:
            ctx.lastActive = time.time()
        return ctx

    def set(self, sessionId: str, context: "ConversationContext") -> None:
        context.lastActive = time.time()
        self._sessions[sessionId] = context

    def delete(self, sessionId: str) -> None:
        self._sessions.pop(sessionId, None)

    def cleanupExpired(self) -> None:
        now = time.time()
        expired = [sid for sid, ctx in self._sessions.items() if now - ctx.lastActive > self.ttlSeconds]
        for sid in expired:
            self._sessions.pop(sid, None)

    cleanup_expired = cleanupExpired


class Turn(BaseModel):
    userMessage: str = Field(alias="user_message")
    botResponse: str = Field(alias="bot_response")
    timestamp: float = Field(default_factory=time.time)
    understanding: Optional[Dict[str, Any]] = None

    model_config = ConfigDict(populate_by_name=True)

    @property
    def user_message(self) -> str:
        return self.userMessage

    @user_message.setter
    def user_message(self, val: str) -> None:
        self.userMessage = val

    @property
    def bot_response(self) -> str:
        return self.botResponse

    @bot_response.setter
    def bot_response(self, val: str) -> None:
        self.botResponse = val


class ConversationContext(BaseModel):
    sessionId: str = Field(alias="session_id")
    turns: List[Turn] = Field(default_factory=list)
    accumulatedCategory: Optional[str] = Field(default=None, alias="accumulated_category")
    accumulatedConstraints: Dict[str, Any] = Field(default_factory=dict, alias="accumulated_constraints")
    accumulatedPreferences: Dict[str, Any] = Field(default_factory=dict, alias="accumulated_preferences")
    accumulatedNegatives: Dict[str, Any] = Field(default_factory=dict, alias="accumulated_negatives")
    entityMemory: EntityDialogueMemory = Field(default_factory=EntityDialogueMemory, alias="entity_memory")
    lastActive: float = Field(default_factory=time.time, alias="last_active")

    model_config = ConfigDict(populate_by_name=True)

    @property
    def session_id(self) -> str:
        return self.sessionId

    @session_id.setter
    def session_id(self, val: str) -> None:
        self.sessionId = val

    @property
    def accumulated_category(self) -> Optional[str]:
        return self.accumulatedCategory

    @accumulated_category.setter
    def accumulated_category(self, val: Optional[str]) -> None:
        self.accumulatedCategory = val

    @property
    def accumulated_constraints(self) -> Dict[str, Any]:
        return self.accumulatedConstraints

    @accumulated_constraints.setter
    def accumulated_constraints(self, val: Dict[str, Any]) -> None:
        self.accumulatedConstraints = val

    @property
    def accumulated_preferences(self) -> Dict[str, Any]:
        return self.accumulatedPreferences

    @accumulated_preferences.setter
    def accumulated_preferences(self, val: Dict[str, Any]) -> None:
        self.accumulatedPreferences = val

    @property
    def accumulated_negatives(self) -> Dict[str, Any]:
        return self.accumulatedNegatives

    @accumulated_negatives.setter
    def accumulated_negatives(self, val: Dict[str, Any]) -> None:
        self.accumulatedNegatives = val

    @property
    def entity_memory(self) -> EntityDialogueMemory:
        return self.entityMemory

    @entity_memory.setter
    def entity_memory(self, val: EntityDialogueMemory) -> None:
        self.entityMemory = val

    @property
    def last_active(self) -> float:
        return self.lastActive

    @last_active.setter
    def last_active(self, val: float) -> None:
        self.lastActive = val

    def resetAccumulated(self) -> None:
        self.accumulatedCategory = None
        self.accumulatedConstraints.clear()
        self.accumulatedPreferences.clear()
        self.accumulatedNegatives.clear()

    reset_accumulated = resetAccumulated

    def updateAccumulatedState(
        self,
        current: UnderstandingResult,
        targetCat: Optional[str] = None,
        curTargetType: str = "PRODUCT",
        **kwargs: Any,
    ) -> None:
        tCat = targetCat if targetCat is not None else kwargs.get("target_cat")
        cTargetType = curTargetType if "curTargetType" in kwargs or curTargetType != "PRODUCT" else kwargs.get("cur_target_type", curTargetType)

        if tCat and cTargetType != "SERVICE":
            oldDom = Taxonomy.getDomain(Taxonomy.normalizeCategory(self.accumulatedCategory))
            newDom = Taxonomy.getDomain(Taxonomy.normalizeCategory(tCat))
            if oldDom and newDom and oldDom != newDom:
                self.resetAccumulated()
            self.accumulatedCategory = tCat
        elif cTargetType == "SERVICE":
            self.accumulatedCategory = None

        curC = current.constraints.model_dump(exclude_none=True) if hasattr(current.constraints, "model_dump") else (current.constraints or {})
        self.accumulatedConstraints.update(curC)
        self.accumulatedConstraints["target_type"] = cTargetType

        curP = current.preferences.model_dump(exclude_none=True) if hasattr(current.preferences, "model_dump") else (current.preferences or {})
        for k, v in curP.items():
            if v is not None:
                self.accumulatedPreferences[k] = v

        curN = current.negative_preferences.model_dump(exclude_none=True) if hasattr(current.negative_preferences, "model_dump") else (current.negative_preferences or {})
        self.accumulatedNegatives.update(curN)

    update_accumulated_state = updateAccumulatedState

    def addTurn(
        self,
        userMessage: str = "",
        botResponse: str = "",
        understanding: Optional[UnderstandingResult] = None,
        **kwargs: Any,
    ) -> int:
        uMsg = userMessage or kwargs.get("user_message", "")
        bResp = botResponse or kwargs.get("bot_response", "")
        und = understanding if understanding is not None else kwargs.get("understanding")
        undDict = und.model_dump() if und else None
        turnIdx = len(self.turns) + 1
        self.turns.append(
            Turn(
                userMessage=uMsg,
                botResponse=bResp,
                understanding=undDict,
            )
        )
        self.lastActive = time.time()
        return turnIdx

    add_turn = addTurn

    def recordEntities(
        self,
        candidates: Optional[List[Any]] = None,
        understanding: Optional[UnderstandingResult] = None,
        userMessage: str = "",
        turnIdx: int = 0,
        **kwargs: Any,
    ) -> None:
        cands = candidates if candidates is not None else kwargs.get("candidates")
        und = understanding if understanding is not None else kwargs.get("understanding")
        uMsg = userMessage or kwargs.get("user_message", "")
        tIdx = turnIdx if "turnIdx" in kwargs or turnIdx != 0 else kwargs.get("turn_idx", turnIdx)

        if cands:
            self.entityMemory.recordCandidates(cands, turnIdx=tIdx)

        if und:
            ents = getattr(und, "entities", {}) or {}
            pCat = ents.get("product_category") or getattr(und, "category", None)
            brands = ents.get("brands", []) if isinstance(ents, dict) else []
            pBrand = getattr(und, "brand", None) or (brands[0] if brands else None)
            if pBrand and (pCat or len(uMsg.split()) <= 8):
                userProdName = f"{pBrand} {pCat or ''}".strip()
                self.entityMemory.recordUserMention(
                    name=userProdName,
                    brand=pBrand,
                    category=pCat,
                    turnIdx=tIdx,
                )

    record_entities = recordEntities

    def getRecentHistory(self, maxTurns: int = 4, **kwargs: Any) -> str:
        mTurns = maxTurns if "maxTurns" in kwargs or maxTurns != 4 else kwargs.get("max_turns", maxTurns)
        recentTurns = self.turns[-mTurns:]
        if not recentTurns:
            return ""

        lines = []
        for t in recentTurns:
            lines.append(f"Khách hàng: {t.userMessage}")
            lines.append(f"Tư vấn viên: {t.botResponse}")
        return "\n".join(lines)

    get_recent_history = getRecentHistory


class ConversationContextManager:

    def __init__(self, store: Optional[BaseSessionStore] = None, ttlSeconds: int = 3600, **kwargs: Any):
        self.ttlSeconds = ttlSeconds if "ttlSeconds" in kwargs or ttlSeconds != 3600 else kwargs.get("ttl_seconds", ttlSeconds)
        self.ttl_seconds = self.ttlSeconds
        self.store = store or InMemorySessionStore(ttlSeconds=self.ttlSeconds)

    def getOrCreate(self, sessionId: Optional[str] = None, **kwargs: Any) -> ConversationContext:
        sId = sessionId or kwargs.get("session_id", "")
        ctx = self.store.get(sId)
        if ctx is None:
            ctx = ConversationContext(sessionId=sId)
            self.store.set(sId, ctx)
        return ctx

    get_or_create = getOrCreate

    def mergeUnderstanding(
        self,
        sessionId: Optional[str] = None,
        current: Optional[UnderstandingResult] = None,
        **kwargs: Any,
    ) -> UnderstandingResult:
        sId = sessionId or kwargs.get("session_id", "")
        cur = current or kwargs.get("current")
        if cur is None:
            raise ValueError("current understanding is required")

        ctx = self.getOrCreate(sId)
        uIntent = getattr(cur.intent, "value", str(cur.intent))

        if uIntent in ("COMPARISON", "PRODUCT_COMPARISON", "ROUTINE_RECOMMENDATION", "ROUTINE"):
            curC = cur.constraints.model_dump(exclude_none=True) if hasattr(cur.constraints, "model_dump") else (cur.constraints or {})
            mergedConstraints = dict(curC)
            if "skin_type" not in mergedConstraints and "skin_type" in ctx.accumulatedConstraints:
                mergedConstraints["skin_type"] = ctx.accumulatedConstraints["skin_type"]

            cls = cur.__class__
            return cls(
                complexity_level=cur.complexity_level,
                goals=cur.goals,
                primary_intent=cur.primary_intent,
                sub_intents=cur.sub_intents,
                entities=cur.entities,
                action_routing=cur.action_routing,
                dialogue_state=cur.dialogue_state,
                intent=cur.intent,
                category=cur.category,
                brand=cur.brand,
                form_factor=cur.form_factor,
                constraints=mergedConstraints,
                preferences=cur.preferences,
                negative_preferences=cur.negative_preferences,
                raw_query=getattr(cur, "raw_query", ""),
                semantic_query=getattr(cur, "semantic_query", ""),
                bm25_query=getattr(cur, "bm25_query", ""),
                information_score=getattr(cur, "information_score", 0.0),
                needs_clarification=cur.needs_clarification,
                clarification_question=cur.clarification_question,
                missing_information=cur.missing_information,
            )

        rawMsg = getattr(cur, "raw_query", "")
        refRes = ctx.entityMemory.resolveReference(rawMsg)
        refEntity = refRes.referenced_entity if refRes.has_reference else None

        targetBrand = cur.brand
        targetCat = cur.category
        semanticQ = getattr(cur, "semantic_query", "")
        bm25Q = getattr(cur, "bm25_query", "")

        if refEntity:
            if not targetBrand and refEntity.brand:
                targetBrand = refEntity.brand
            if not targetCat and refEntity.category:
                targetCat = refEntity.category
            if refEntity.product_name.lower() not in bm25Q.lower():
                bm25Q = f"{refEntity.product_name} {bm25Q}".strip()
            if refEntity.product_name.lower() not in semanticQ.lower():
                semanticQ = f"{refEntity.product_name} {semanticQ}".strip()

        curTargetType = None
        if hasattr(cur, "constraints") and isinstance(cur.constraints, dict):
            curTargetType = cur.constraints.get("target_type")
        if not curTargetType and hasattr(cur, "entities") and isinstance(cur.entities, dict):
            curTargetType = cur.entities.get("target_type")
        curTargetType = str(curTargetType or "PRODUCT").upper()

        oldTargetType = str(ctx.accumulatedConstraints.get("target_type", "PRODUCT")).upper()

        if curTargetType != oldTargetType:
            ctx.resetAccumulated()
            targetBrand = None
            targetCat = None
            if curTargetType == "SERVICE":
                cur.category = None
                cur.brand = None

        # Luôn cập nhật ngữ cảnh tích lũy với bất kỳ thông tin nào người dùng đã cung cấp
        ctx.updateAccumulatedState(
            current=cur,
            targetCat=targetCat,
            curTargetType=curTargetType,
        )

        cls = cur.__class__
        finalCategory = None if curTargetType == "SERVICE" else (ctx.accumulatedCategory or targetCat)
        finalBrand = None if curTargetType == "SERVICE" else (targetBrand or ctx.accumulatedConstraints.get("brand"))

        # Tính toán lại điểm độ đầy đủ sau khi hợp nhất với ngữ cảnh tích lũy
        mergedDict = {
            "category": finalCategory,
            "brand": finalBrand,
            "constraints": dict(ctx.accumulatedConstraints),
            "preferences": dict(ctx.accumulatedPreferences),
            "negative_preferences": dict(ctx.accumulatedNegatives),
            "entities": cur.entities if isinstance(cur.entities, dict) else {},
            "raw_query": rawMsg,
        }
        recalcScore = calculateInformationScore(mergedDict, rawMsg)

        finalNeedsClarify = cur.needs_clarification
        finalAction = cur.action_routing
        finalPrimary = cur.primary_intent
        finalIntent = cur.intent
        finalClarificationQ = cur.clarification_question

        # Nếu đã có Danh mục/Dịch vụ VÀ đã có thuộc tính da liễu từ ngữ cảnh (loại da, vấn đề mụn/thâm, hoặc hãng):
        hasContextDetails = bool(
            ctx.accumulatedConstraints.get("skin_type")
            or ctx.accumulatedPreferences.get("concerns")
            or ctx.accumulatedConstraints.get("brand")
        )
        if finalCategory and hasContextDetails and recalcScore >= 3.0:
            finalNeedsClarify = False
            finalAction = ActionRouting.CONSTRAINED_HYBRID_SEARCH.value
            finalPrimary = PrimaryIntent.TU_VAN_SAN_PHAM.value
            finalIntent = Intent.PRODUCT_SEARCH
            finalClarificationQ = None

            skinStr = ctx.accumulatedConstraints.get("skin_type", "")
            concerns = ctx.accumulatedPreferences.get("concerns", [])
            conStr = " ".join(concerns) if isinstance(concerns, list) else str(concerns or "")
            if not semanticQ or semanticQ == rawMsg or len(semanticQ.split()) <= 3:
                semanticQ = f"{finalCategory} cho {skinStr} {conStr}".strip()
                bm25Q = semanticQ

        return cls(
            complexity_level=cur.complexity_level,
            information_score=recalcScore,
            goals=cur.goals,
            primary_intent=finalPrimary,
            sub_intents=cur.sub_intents,
            target_type=getattr(cur, "target_type", curTargetType) or "PRODUCT",
            entities=cur.entities,
            action_routing=finalAction,
            dialogue_state={
                "needs_clarification": finalNeedsClarify,
                "clarification_question": finalClarificationQ,
                "missing_information": [] if not finalNeedsClarify else cur.missing_information,
            },
            intent=finalIntent,
            category=finalCategory,
            brand=finalBrand,
            form_factor=cur.form_factor,
            constraints=dict(ctx.accumulatedConstraints),
            preferences=dict(ctx.accumulatedPreferences),
            negative_preferences=dict(ctx.accumulatedNegatives),
            raw_query=getattr(cur, "raw_query", ""),
            semantic_query=semanticQ,
            bm25_query=bm25Q,
            needs_clarification=finalNeedsClarify,
            clarification_question=finalClarificationQ,
            missing_information=[] if not finalNeedsClarify else cur.missing_information,
            target_quantity=getattr(cur, "target_quantity", None),
        )

    merge_understanding = mergeUnderstanding

    def recordTurn(
        self,
        sessionId: Optional[str] = None,
        userMessage: str = "",
        botResponse: str = "",
        understanding: Optional[UnderstandingResult] = None,
        candidates: Optional[List[Any]] = None,
        **kwargs: Any,
    ) -> None:
        sId = sessionId or kwargs.get("session_id", "")
        uMsg = userMessage or kwargs.get("user_message", "")
        bResp = botResponse or kwargs.get("bot_response", "")
        und = understanding if understanding is not None else kwargs.get("understanding")
        cands = candidates if candidates is not None else kwargs.get("candidates")

        ctx = self.getOrCreate(sId)
        turnIdx = ctx.addTurn(
            userMessage=uMsg,
            botResponse=bResp,
            understanding=und,
        )
        ctx.recordEntities(
            candidates=cands,
            understanding=und,
            userMessage=uMsg,
            turnIdx=turnIdx,
        )

    record_turn = recordTurn

    def getHistoryFormatted(
        self,
        sessionId: Optional[str] = None,
        maxTurns: int = 4,
        **kwargs: Any,
    ) -> str:
        sId = sessionId or kwargs.get("session_id", "")
        mTurns = maxTurns if "maxTurns" in kwargs or sessionId is not None else kwargs.get("max_turns", maxTurns)
        ctx = self.getOrCreate(sId)
        return ctx.getRecentHistory(maxTurns=mTurns)

    get_history_formatted = getHistoryFormatted


conversationManager = ConversationContextManager()
conversation_manager = conversationManager
