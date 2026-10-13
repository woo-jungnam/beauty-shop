import re
from enum import Enum
from typing import Any, Dict, List, Optional
from pydantic import BaseModel, ConfigDict, Field


class FocusTransition(str, Enum):
    INIT = "INIT"
    CONTINUATION = "CONTINUATION"
    SHIFT = "SHIFT"
    RETURN_TO_PREVIOUS = "RETURN_TO_PREVIOUS"
    RETURN_TO_FIRST = "RETURN_TO_FIRST"
    RETURN_TO_ORDINAL = "RETURN_TO_ORDINAL"


class TrackedProductEntity(BaseModel):
    productId: Optional[Any] = Field(default=None, alias="product_id")
    productName: str = Field(alias="product_name")
    brand: Optional[str] = None
    category: Optional[str] = None
    price: Optional[float] = None
    turnIdx: int = Field(default=0, alias="turn_idx")
    role: str = "BOT_RECOMMENDED"
    attributes: Dict[str, Any] = Field(default_factory=dict)

    model_config = ConfigDict(populate_by_name=True)

    @property
    def product_id(self) -> Optional[Any]:
        return self.productId

    @product_id.setter
    def product_id(self, val: Optional[Any]) -> None:
        self.productId = val

    @property
    def product_name(self) -> str:
        return self.productName

    @product_name.setter
    def product_name(self, val: str) -> None:
        self.productName = val

    @property
    def turn_idx(self) -> int:
        return self.turnIdx

    @turn_idx.setter
    def turn_idx(self, val: int) -> None:
        self.turnIdx = val

    @property
    def canonicalKey(self) -> str:
        return self.productName.strip().lower()

    canonical_key = canonicalKey


class ReferenceResolutionResult(BaseModel):
    hasReference: bool = Field(default=False, alias="has_reference")
    referenceType: Optional[str] = None
    matchedPhrase: Optional[str] = None
    referencedEntity: Optional[TrackedProductEntity] = None
    activeFocusEntity: Optional[TrackedProductEntity] = None
    transition: FocusTransition = FocusTransition.CONTINUATION

    model_config = ConfigDict(populate_by_name=True)

    @property
    def has_reference(self) -> bool:
        return self.hasReference

    @has_reference.setter
    def has_reference(self, val: bool) -> None:
        self.hasReference = val

    @property
    def reference_type(self) -> Optional[str]:
        return self.referenceType

    @reference_type.setter
    def reference_type(self, val: Optional[str]) -> None:
        self.referenceType = val

    @property
    def matched_phrase(self) -> Optional[str]:
        return self.matchedPhrase

    @matched_phrase.setter
    def matched_phrase(self, val: Optional[str]) -> None:
        self.matchedPhrase = val

    @property
    def referenced_entity(self) -> Optional[TrackedProductEntity]:
        return self.referencedEntity

    @referenced_entity.setter
    def referenced_entity(self, val: Optional[TrackedProductEntity]) -> None:
        self.referencedEntity = val

    @property
    def active_focus_entity(self) -> Optional[TrackedProductEntity]:
        return self.activeFocusEntity

    @active_focus_entity.setter
    def active_focus_entity(self, val: Optional[TrackedProductEntity]) -> None:
        self.activeFocusEntity = val


class EntityDialogueMemory(BaseModel):
    focusStack: List[str] = Field(default_factory=list, alias="focus_stack")
    entityCatalog: Dict[str, TrackedProductEntity] = Field(default_factory=dict, alias="entity_catalog")
    turnEntityHistory: Dict[int, List[str]] = Field(default_factory=dict, alias="turn_entity_history")
    lastTransition: FocusTransition = Field(default=FocusTransition.INIT, alias="last_transition")

    model_config = ConfigDict(populate_by_name=True)

    @property
    def focus_stack(self) -> List[str]:
        return self.focusStack

    @focus_stack.setter
    def focus_stack(self, val: List[str]) -> None:
        self.focusStack = val

    @property
    def entity_catalog(self) -> Dict[str, TrackedProductEntity]:
        return self.entityCatalog

    @entity_catalog.setter
    def entity_catalog(self, val: Dict[str, TrackedProductEntity]) -> None:
        self.entityCatalog = val

    @property
    def turn_entity_history(self) -> Dict[int, List[str]]:
        return self.turnEntityHistory

    @turn_entity_history.setter
    def turn_entity_history(self, val: Dict[int, List[str]]) -> None:
        self.turnEntityHistory = val

    @property
    def last_transition(self) -> FocusTransition:
        return self.lastTransition

    @last_transition.setter
    def last_transition(self, val: FocusTransition) -> None:
        self.lastTransition = val

    @staticmethod
    def normalizeName(name: str) -> str:
        if not name:
            return ""
        return re.sub(r"\s+", " ", name.strip().lower())

    normalize_name = normalizeName

    def getActiveEntity(self) -> Optional[TrackedProductEntity]:
        if not self.focusStack:
            return None
        return self.entityCatalog.get(self.focusStack[-1])

    get_active_entity = getActiveEntity

    def getPreviousEntity(self) -> Optional[TrackedProductEntity]:
        if len(self.focusStack) < 2:
            return self.getActiveEntity()
        return self.entityCatalog.get(self.focusStack[-2])

    get_previous_entity = getPreviousEntity

    def getFirstEntity(self) -> Optional[TrackedProductEntity]:
        if not self.focusStack:
            return None
        return self.entityCatalog.get(self.focusStack[0])

    get_first_entity = getFirstEntity

    def recordCandidate(
        self,
        candidateOrProduct: Any,
        turnIdx: int = 0,
        role: str = "BOT_RECOMMENDED",
        makeActive: bool = True,
        **kwargs: Any,
    ) -> Optional[TrackedProductEntity]:
        candOrProd = candidateOrProduct if candidateOrProduct is not None else kwargs.get("candidate_or_product")
        tIdx = turnIdx if "turnIdx" in kwargs or turnIdx != 0 else kwargs.get("turn_idx", turnIdx)
        mActive = makeActive if "makeActive" in kwargs or not makeActive else kwargs.get("make_active", makeActive)

        if candOrProd is None:
            return None

        prod = getattr(candOrProd, "product", candOrProd)
        pId = getattr(prod, "id", None) or (prod.get("id") if isinstance(prod, dict) else None)
        pName = getattr(prod, "name", None) or (prod.get("name") if isinstance(prod, dict) else None)
        pBrand = getattr(prod, "brand", None) or (prod.get("brand") if isinstance(prod, dict) else None)
        pCat = getattr(prod, "category", None) or (prod.get("category") if isinstance(prod, dict) else None)
        pPrice = getattr(prod, "price", None) or (prod.get("price") if isinstance(prod, dict) else None)

        if not pName:
            return None

        key = self.normalizeName(pName)
        tracked = TrackedProductEntity(
            productId=pId,
            productName=pName,
            brand=pBrand,
            category=pCat,
            price=float(pPrice) if pPrice is not None else None,
            turnIdx=tIdx,
            role=role,
            attributes={
                "skin_type": getattr(prod, "skinType", getattr(prod, "skin_type", None)) or (prod.get("skin_type") if isinstance(prod, dict) else None),
                "concerns": getattr(prod, "concerns", None) or (prod.get("concerns") if isinstance(prod, dict) else None),
            },
        )

        self.entityCatalog[key] = tracked

        if tIdx not in self.turnEntityHistory:
            self.turnEntityHistory[tIdx] = []
        if key not in self.turnEntityHistory[tIdx]:
            self.turnEntityHistory[tIdx].append(key)

        if mActive:
            if key in self.focusStack:
                self.focusStack.remove(key)
            self.focusStack.append(key)

        return tracked

    record_candidate = recordCandidate

    def recordCandidates(self, candidates: List[Any], turnIdx: int = 0, **kwargs: Any) -> List[TrackedProductEntity]:
        tIdx = turnIdx if "turnIdx" in kwargs or turnIdx != 0 else kwargs.get("turn_idx", turnIdx)
        if not candidates:
            return []

        recorded = []
        for idx, cand in enumerate(candidates):
            isPrimary = (idx == 0)
            ent = self.recordCandidate(cand, turnIdx=tIdx, role="BOT_RECOMMENDED", makeActive=isPrimary)
            if ent:
                recorded.append(ent)
        return recorded

    record_candidates = recordCandidates

    def recordUserMention(
        self,
        name: str,
        brand: Optional[str] = None,
        category: Optional[str] = None,
        turnIdx: int = 0,
        **kwargs: Any,
    ) -> Optional[TrackedProductEntity]:
        tIdx = turnIdx if "turnIdx" in kwargs or turnIdx != 0 else kwargs.get("turn_idx", turnIdx)
        if not name:
            return None
        key = self.normalizeName(name)
        tracked = TrackedProductEntity(
            productId=None,
            productName=name.strip(),
            brand=brand,
            category=category,
            turnIdx=tIdx,
            role="USER_MENTIONED",
        )
        self.entityCatalog[key] = tracked
        if key in self.focusStack:
            self.focusStack.remove(key)
        self.focusStack.append(key)
        return tracked

    record_user_mention = recordUserMention

    def resolveReference(self, message: str) -> ReferenceResolutionResult:
        if not message or not self.focusStack:
            return ReferenceResolutionResult(
                hasReference=False,
                activeFocusEntity=self.getActiveEntity(),
            )

        msgLower = message.lower().strip()

        firstPatterns = [
            r"\b(cái đầu tiên|sản phẩm đầu tiên|loại đầu tiên|món đầu tiên|sp đầu tiên|cái thứ nhất|sản phẩm thứ nhất|loại thứ nhất|cái đầu)\b"
        ]
        for pat in firstPatterns:
            m = re.search(pat, msgLower)
            if m:
                firstEnt = self.getFirstEntity()
                self.lastTransition = FocusTransition.RETURN_TO_FIRST
                return ReferenceResolutionResult(
                    hasReference=True,
                    referenceType="FIRST_FOCUS",
                    matchedPhrase=m.group(0),
                    referencedEntity=firstEnt,
                    activeFocusEntity=self.getActiveEntity(),
                    transition=FocusTransition.RETURN_TO_FIRST,
                )

        ordinalMatches = [
            (r"\b(cái thứ hai|sản phẩm thứ hai|loại thứ hai|thứ hai)\b", 1),
            (r"\b(cái thứ ba|sản phẩm thứ ba|loại thứ ba|thứ ba)\b", 2),
        ]
        for pat, idx in ordinalMatches:
            m = re.search(pat, msgLower)
            if m and len(self.focusStack) > idx:
                targetKey = self.focusStack[idx]
                targetEnt = self.entityCatalog.get(targetKey)
                self.lastTransition = FocusTransition.RETURN_TO_ORDINAL
                return ReferenceResolutionResult(
                    hasReference=True,
                    referenceType="ORDINAL_FOCUS",
                    matchedPhrase=m.group(0),
                    referencedEntity=targetEnt,
                    activeFocusEntity=self.getActiveEntity(),
                    transition=FocusTransition.RETURN_TO_ORDINAL,
                )

        prevPatterns = [
            r"\b(cái hồi nãy|cái lúc nãy|cái vừa rồi|cái vừa nãy|cái ban nãy|sản phẩm hồi nãy|sản phẩm lúc nãy|sản phẩm vừa rồi|sp hồi nãy|sp lúc nãy|cái trước|loại trước|sản phẩm trước|loại vừa rồi|món trước)\b",
            r"\b(hồi nãy|lúc nãy|vừa nãy|ban nãy)\b",
        ]
        for pat in prevPatterns:
            m = re.search(pat, msgLower)
            if m:
                prevEnt = self.getPreviousEntity()
                self.lastTransition = FocusTransition.RETURN_TO_PREVIOUS
                return ReferenceResolutionResult(
                    hasReference=True,
                    referenceType="PREVIOUS_FOCUS",
                    matchedPhrase=m.group(0),
                    referencedEntity=prevEnt,
                    activeFocusEntity=self.getActiveEntity(),
                    transition=FocusTransition.RETURN_TO_PREVIOUS,
                )

        curPatterns = [
            r"\b(nó|em này|sản phẩm này|sp này|cái này|chai này|hũ này|tuýp này|bé này)\b"
        ]
        for pat in curPatterns:
            m = re.search(pat, msgLower)
            if m:
                curEnt = self.getActiveEntity()
                self.lastTransition = FocusTransition.CONTINUATION
                return ReferenceResolutionResult(
                    hasReference=True,
                    referenceType="CURRENT_FOCUS",
                    matchedPhrase=m.group(0),
                    referencedEntity=curEnt,
                    activeFocusEntity=curEnt,
                    transition=FocusTransition.CONTINUATION,
                )

        return ReferenceResolutionResult(
            hasReference=False,
            activeFocusEntity=self.getActiveEntity(),
            transition=FocusTransition.CONTINUATION,
        )

    resolve_reference = resolveReference

    def getContextSummary(self) -> Dict[str, Any]:
        active = self.getActiveEntity()
        summary: Dict[str, Any] = {
            "active_focus_product": active.productName if active else None,
            "active_focus_brand": active.brand if active else None,
            "active_focus_category": active.category if active else None,
            "focus_stack_depth": len(self.focusStack),
            "recent_entities": [],
        }

        for key in reversed(self.focusStack[-3:]):
            ent = self.entityCatalog.get(key)
            if ent:
                summary["recent_entities"].append({
                    "name": ent.productName,
                    "brand": ent.brand,
                    "category": ent.category,
                    "turn_idx": ent.turnIdx,
                })

        return summary

    get_context_summary = getContextSummary


entityMemory = EntityDialogueMemory()
entity_memory = entityMemory
