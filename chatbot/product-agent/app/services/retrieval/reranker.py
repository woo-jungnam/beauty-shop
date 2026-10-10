import math
import re
from typing import List, Optional, Set

from app.core.config import getSettings
from app.core.logging import LatencyTracker, getLogger
from app.core.taxonomy import ProductDomain, Taxonomy
from app.schemas.query import UnderstandingResult
from app.schemas.retrieval import RerankedCandidate, ScoredCandidate

logger = getLogger("rerankerService")
settings = getSettings()


class ProductReranker:

    @staticmethod
    def computeSocialProof(
        rating: float = 5.0,
        totalReviews: int = 0,
        totalSold: int = 0,
        **kwargs,
    ) -> tuple[float, List[str]]:
        tReviews = totalReviews if "totalReviews" in kwargs or totalReviews != 0 else kwargs.get("total_reviews", totalReviews)
        tSold = totalSold if "totalSold" in kwargs or totalSold != 0 else kwargs.get("total_sold", totalSold)

        notes: List[str] = []
        v = float(max(0, tReviews))
        s = float(max(0, tSold))
        r = float(rating) if rating > 0 else 5.0

        m, cPrior = 5.0, 4.5
        bayesRating = (v * r + m * cPrior) / (v + m)
        ratingScore = max(0.0, (bayesRating - 3.0) * 0.8)

        reviewScore = min(2.0, math.log10(v + 1.0) * 0.6)
        salesScore = min(2.0, math.log10(s + 1.0) * 0.4)

        totalSocialScore = ratingScore + reviewScore + salesScore

        if tReviews >= 50 or tSold >= 100:
            notes.append(f"Được tin dùng ({int(v):,} đánh giá, {int(s):,} đã bán)")
        elif r >= 4.8 and v > 0:
            notes.append(f"Đánh giá xuất sắc ({r:.1f} sao)")

        return totalSocialScore, notes

    compute_social_proof = computeSocialProof

    @staticmethod
    def checkSkinTypeMatch(
        userSkin: str = "",
        productSkins: Optional[List[str]] = None,
        **kwargs,
    ) -> str:
        uSkin = userSkin or kwargs.get("user_skin", "")
        prodSkinsList = productSkins if productSkins is not None else kwargs.get("product_skins", [])
        if not uSkin or not prodSkinsList:
            return "neutral"

        prodSkins = [s.lower() for s in prodSkinsList]
        uSkinLower = uSkin.lower()

        if uSkinLower in prodSkins:
            return "exact"
        if "all" in prodSkins or "mọi loại da" in prodSkins:
            return "neutral"
        if "sensitive" in prodSkins:
            return "neutral"
        return "mismatch"

    _check_skin_type_match = checkSkinTypeMatch

    @staticmethod
    def checkIngredientMatch(
        userIngredients: Optional[List[str]] = None,
        productIngredientsText: str = "",
        **kwargs,
    ) -> tuple[float, List[str]]:
        uIngr = userIngredients if userIngredients is not None else kwargs.get("user_ingredients", [])
        pIngrText = productIngredientsText or kwargs.get("product_ingredients_text", "")
        if not uIngr or not pIngrText:
            return 0.0, []

        ingrLower = pIngrText.lower()
        matched = [ingr for ingr in uIngr if ingr.lower() in ingrLower]
        score = len(matched) * 2.0
        return score, matched

    _check_ingredient_match = checkIngredientMatch

    @staticmethod
    def checkNegativePreferences(
        negativePrefs: Optional[dict] = None,
        productIngredientsText: str = "",
        productName: str = "",
        **kwargs,
    ) -> tuple[float, List[str], List[str]]:
        negPrefs = negativePrefs if negativePrefs is not None else kwargs.get("negative_prefs", {})
        prodIngrText = productIngredientsText or kwargs.get("product_ingredients_text", "")
        prodName = productName or kwargs.get("product_name", "")

        explanations: List[str] = []
        penalties: List[str] = []
        score = 0.0

        if not negPrefs:
            return score, explanations, penalties

        ingrLower = prodIngrText.lower() if prodIngrText else ""
        nameLower = prodName.lower() if prodName else ""

        if negPrefs.get("alcohol_free"):
            harshAlcohols = ["alcohol denat", "ethanol", "cồn khô", "sd alcohol", "isopropyl alcohol"]
            hasAlcohol = any(a in ingrLower for a in harshAlcohols) or ("cồn" in ingrLower and "không cồn" not in ingrLower)
            if hasAlcohol:
                score -= 100.0
                penalties.append("Chứa cồn khô vi phạm tiêu chí kiêng kỵ")
            else:
                score += 2.5
                explanations.append("Công thức 0% cồn dịu nhẹ")

        if negPrefs.get("fragrance_free"):
            fragrances = ["fragrance", "parfum", "hương liệu"]
            hasFragrance = any(f in ingrLower for f in fragrances) and "không hương liệu" not in ingrLower
            if hasFragrance:
                score -= 100.0
                penalties.append("Chứa hương liệu vi phạm tiêu chí kiêng kỵ")
            else:
                score += 2.5
                explanations.append("Không hương liệu (fragrance-free)")

        excluded = negPrefs.get("excluded_ingredients") or []
        if isinstance(excluded, str):
            excluded = [excluded]
        for ex in excluded:
            exClean = ex.strip().lower()
            if not exClean or exClean in ("cồn", "alcohol", "hương liệu", "fragrance"):
                continue
            if exClean in ingrLower or exClean in nameLower:
                score -= 100.0
                penalties.append(f"Chứa thành phần kiêng kỵ ({ex})")

        return score, explanations, penalties

    _check_negative_preferences = checkNegativePreferences

    @staticmethod
    def checkFormFactorAlignment(
        userFormFactor: Optional[str] = None,
        userCategory: Optional[str] = None,
        rawQuery: str = "",
        prodName: str = "",
        prodCategory: str = "",
        **kwargs,
    ) -> tuple[float, List[str], List[str]]:
        uFormFactor = userFormFactor if userFormFactor is not None else kwargs.get("user_form_factor")
        uCategory = userCategory if userCategory is not None else kwargs.get("user_category")
        qRaw = (rawQuery or kwargs.get("raw_query", "")).lower()
        pName = (prodName or kwargs.get("prod_name", "")).lower()
        pCategory = prodCategory or kwargs.get("prod_category", "")

        explanations: List[str] = []
        penalties: List[str] = []
        score = 0.0

        uff = (uFormFactor or "").upper()
        isTool = any(k in pName for k in ["bông tẩy trang", "cotton pads", "bông trang điểm", "mút trang điểm", "cọ "])
        userWantsLiquid = "nước tẩy trang" in qRaw or "dầu tẩy trang" in qRaw or uff == "FLUID" or "dung dịch" in qRaw
        userWantsTool = "bông tẩy trang" in qRaw or uff == "TOOL" or "mút" in qRaw

        if userWantsLiquid:
            if isTool:
                score -= 20.0
                penalties.append("Dạng dụng cụ bông tẩy trang (khác dạng nước/dung dịch yêu cầu)")
            elif "nước tẩy trang" in pName or "micellar" in pName:
                score += 4.0
                explanations.append("Đúng dạng nước tẩy trang micellar")
        elif userWantsTool:
            if isTool:
                score += 5.0
                explanations.append("Đúng dụng cụ bông tẩy trang")
            else:
                score -= 20.0
                penalties.append("Không phải dạng dụng cụ bông tẩy trang yêu cầu")

        return score, explanations, penalties

    _check_form_factor_alignment = checkFormFactorAlignment

    @staticmethod
    def getBaseProductKey(prodName: str, prodBrand: str, **kwargs) -> str:
        pName = prodName or kwargs.get("prod_name", "")
        pBrand = prodBrand or kwargs.get("prod_brand", "")
        clean = pName.lower()
        clean = re.sub(r"mã\s*\d+", "", clean)
        clean = re.sub(r"\b\d+\s*ml\b", "", clean)
        clean = re.sub(r"\b\d+\s*g\b", "", clean)
        clean = re.sub(r"\b(micellar\s*water|micellar)\b", "", clean)
        clean = re.sub(r"\b(dịu nhẹ cho da nhạy cảm|dịu nhẹ|cho da nhạy cảm)\b", "", clean)
        clean = re.sub(r"\s+", " ", clean).strip()
        return f"{pBrand.lower()}:{clean}"

    _get_base_product_key = getBaseProductKey

    @staticmethod
    def evaluateCategoryAndDomain(
        userCategory: Optional[str] = None,
        targetDomain: Optional[ProductDomain] = None,
        prodCategory: Optional[str] = None,
        prodName: str = "",
        prodDesc: str = "",
        **kwargs,
    ) -> tuple[float, List[str], List[str]]:
        uCategory = userCategory if userCategory is not None else kwargs.get("user_category")
        tDomain = targetDomain if targetDomain is not None else kwargs.get("target_domain")
        pCategory = prodCategory if prodCategory is not None else kwargs.get("prod_category")
        pName = prodName or kwargs.get("prod_name", "")
        pDesc = prodDesc or kwargs.get("prod_desc", "")

        explanations: List[str] = []
        penalties: List[str] = []
        score = 0.0

        if not pCategory:
            return score, explanations, penalties

        prodDomain = Taxonomy.getProductDomain(pCategory, pName, pDesc)

        if uCategory:
            normUserCat = Taxonomy.normalizeCategory(uCategory)
            if normUserCat and pCategory.lower() == normUserCat.lower():
                score += 5.0
                explanations.append(f"Đúng danh mục yêu cầu ({pCategory})")
            else:
                userDomain = Taxonomy.getDomain(normUserCat) if normUserCat else tDomain
                if userDomain and prodDomain and userDomain == prodDomain:
                    score -= 8.0
                    penalties.append(f"Khác loại sản phẩm yêu cầu ({pCategory} thay vì {normUserCat or uCategory})")
                else:
                    score -= 20.0
                    penalties.append(f"Sai lệch ngành hàng ({pCategory})")
        elif tDomain:
            if prodDomain and prodDomain != tDomain:
                score -= 20.0
                penalties.append(f"Ngành hàng không khớp nhu cầu ({pCategory})")

        return score, explanations, penalties

    _evaluate_category_and_domain = evaluateCategoryAndDomain

    def rerank(
        self,
        candidates: List[ScoredCandidate],
        understanding: UnderstandingResult,
        topK: Optional[int] = None,
        **kwargs,
    ) -> List[RerankedCandidate]:
        limitTopK = topK if topK is not None else kwargs.get("top_k")
        targetQty = getattr(understanding, "targetQuantity", getattr(understanding, "target_quantity", None))
        if targetQty and targetQty > 0:
            limit = targetQty
        else:
            limit = limitTopK or settings.rerankTopK

        if not candidates:
            return []

        tracker = LatencyTracker("rerankCandidates")
        with tracker.measure("scoring"):
            cDict = understanding.constraints if isinstance(understanding.constraints, dict) else (understanding.constraints.model_dump() if hasattr(understanding.constraints, "model_dump") else {})
            pDict = understanding.preferences if isinstance(understanding.preferences, dict) else (understanding.preferences.model_dump() if hasattr(understanding.preferences, "model_dump") else {})
            nDict = understanding.negativePreferences if hasattr(understanding, "negativePreferences") and isinstance(understanding.negativePreferences, dict) else (understanding.negative_preferences if isinstance(understanding.negative_preferences, dict) else {})

            userSkin = cDict.get("skin_type")
            userMaxPrice = cDict.get("max_price")
            userBrand = cDict.get("brand")
            userFormFactor = understanding.formFactor or cDict.get("form_factor")
            rawUserCat = cDict.get("category") or understanding.category
            userCategory = Taxonomy.normalizeCategory(str(rawUserCat)) if rawUserCat else None
            if userCategory and Taxonomy.getDomain(userCategory) is None:
                userCategory = None

            rawConcerns = pDict.get("concerns") or pDict.get("concern") or []
            if isinstance(rawConcerns, str):
                rawConcerns = [rawConcerns]
            userConcerns = [c.lower() for c in rawConcerns if c]

            rawIngredients = pDict.get("ingredients") or []
            if isinstance(rawIngredients, str):
                rawIngredients = [rawIngredients]
            userIngredients = [i for i in rawIngredients if i]

            query_str = (getattr(understanding, "rawQuery", None) or getattr(understanding, "semanticQuery", None) or getattr(understanding, "raw_query", "") or "") if understanding else ""
            targetDomain = Taxonomy.getDomain(userCategory) if userCategory else Taxonomy.inferDomainFromQuery(
                queryText=query_str,
                concerns=userConcerns,
            )

            reranked: List[RerankedCandidate] = []

            for cand in candidates:
                prod = cand.product
                if not prod:
                    continue

                score = cand.rrfScore * 30.0
                explanations: List[str] = []
                penalties: List[str] = []

                catScore, catExp, catPen = self.evaluateCategoryAndDomain(
                    userCategory=userCategory,
                    targetDomain=targetDomain,
                    prodCategory=prod.category,
                    prodName=prod.name or "",
                    prodDesc=getattr(prod, "description", "") or "",
                )
                score += catScore
                explanations.extend(catExp)
                penalties.extend(catPen)

                ffScore, ffExp, ffPen = self.checkFormFactorAlignment(
                    userFormFactor=userFormFactor,
                    userCategory=userCategory,
                    rawQuery=understanding.rawQuery,
                    prodName=prod.name or "",
                    prodCategory=prod.category,
                )
                score += ffScore
                explanations.extend(ffExp)
                penalties.extend(ffPen)

                negScore, negExp, negPen = self.checkNegativePreferences(
                    negativePrefs=nDict,
                    productIngredientsText=getattr(prod, "ingredients", "") or "",
                    productName=prod.name or "",
                )
                score += negScore
                explanations.extend(negExp)
                penalties.extend(negPen)

                rawQ = (understanding.rawQuery or "").lower()
                codeMatch = re.search(r"\b(mã\s*\d+)\b", rawQ, re.I)
                if codeMatch:
                    targetCode = codeMatch.group(1).lower().replace(" ", "")
                    pNameClean = (prod.name or "").lower().replace(" ", "")
                    if targetCode in pNameClean:
                        score += 50.0
                        explanations.append(f"Khớp chính xác mã sản phẩm ({codeMatch.group(1)})")
                    else:
                        score -= 50.0
                        penalties.append(f"Khác mã sản phẩm yêu cầu ({codeMatch.group(1)})")

                prodCategory = prod.category
                prodSkinType = getattr(prod, "skinType", getattr(prod, "skin_type", None))
                if Taxonomy.isSkinTypeApplicable(prodCategory):
                    if userSkin and prodSkinType:
                        matchType = self.checkSkinTypeMatch(userSkin, prodSkinType)
                        cleanSkin = userSkin.strip()
                        if cleanSkin.lower().startswith("da "):
                            cleanSkin = cleanSkin[3:].strip()
                        if matchType == "exact":
                            score += 3.0
                            explanations.append(f"Phù hợp chuẩn loại da {cleanSkin}")
                        elif matchType == "neutral":
                            score += 1.0
                        else:
                            score -= 8.0
                            penalties.append(f"Không phù hợp loại da {cleanSkin}")

                matchedConcerns = []
                if userConcerns and prod.concerns:
                    prodConcerns = [c.lower() for c in prod.concerns]
                    for uc in userConcerns:
                        if uc in prodConcerns or any(uc in pc for pc in prodConcerns):
                            matchedConcerns.append(uc)
                            score += 2.0

                if matchedConcerns:
                    explanations.append(f"Giải quyết trực tiếp vấn đề: {', '.join(matchedConcerns)}")

                ingrScore, matchedIngr = self.checkIngredientMatch(
                    userIngredients,
                    getattr(prod, "ingredients", "") or "",
                )
                score += ingrScore
                if matchedIngr:
                    explanations.append(f"Chứa thành phần yêu cầu: {', '.join(matchedIngr)}")

                if userBrand and prod.brand:
                    if userBrand.lower() in prod.brand.lower():
                        score += 3.0
                        explanations.append(f"Đúng thương hiệu yêu cầu ({prod.brand})")

                uIntent = getattr(understanding.intent, "value", str(understanding.intent))
                isComparison = uIntent in ("COMPARISON", "PRODUCT_COMPARISON")

                if isComparison:
                    qRawLower = (understanding.rawQuery or "").lower()
                    pNameLower = prod.name.lower()
                    if prod.brand and prod.brand.lower() in qRawLower:
                        score += 20.0
                        explanations.append(f"Đúng thương hiệu so sánh ({prod.brand})")
                    nameWords = [w for w in pNameLower.split() if len(w) >= 3 and w not in ("tinh", "chất", "serum", "kem", "dưỡng")]
                    overlapCount = sum(1 for w in nameWords if w in qRawLower)
                    if overlapCount > 0:
                        score += (overlapCount * 10.0)
                        explanations.append(f"Khớp tên sản phẩm so sánh ({prod.name})")
                elif userMaxPrice is not None:
                    if prod.price <= userMaxPrice:
                        score += 3.0
                        explanations.append(f"Giá hợp lý ({prod.price:,.0f}đ ≤ {userMaxPrice:,.0f}đ)")
                    else:
                        score -= 100.0
                        penalties.append(f"Vượt ngân sách ({prod.price:,.0f}đ > {userMaxPrice:,.0f}đ)")

                prodReviews = getattr(prod, "totalReviews", getattr(prod, "total_reviews", 0)) or 0
                prodSold = getattr(prod, "totalSold", getattr(prod, "total_sold", 0)) or 0
                socialScore, socialNotes = self.computeSocialProof(
                    rating=prod.rating or 5.0,
                    totalReviews=prodReviews,
                    totalSold=prodSold,
                )
                score += socialScore
                explanations.extend(socialNotes)

                allNotes = explanations + penalties
                explanationText = "; ".join(allNotes) if allNotes else "Sản phẩm phù hợp với nhu cầu tìm kiếm"

                reranked.append(
                    RerankedCandidate(
                        productId=prod.id,
                        product=prod,
                        initialScore=round(cand.rrfScore, 4),
                        rerankScore=round(score, 4),
                        relevanceExplanation=explanationText,
                    )
                )

            reranked.sort(key=lambda x: x.rerankScore, reverse=True)

            results: List[RerankedCandidate] = []
            seenBaseKeys: Set[str] = set()
            seenRoutineCategories: Set[str] = set()
            uIntentStr = getattr(understanding.intent, "value", str(understanding.intent))
            isRoutine = uIntentStr in ("ROUTINE_RECOMMENDATION", "ROUTINE")

            reqTType = cDict.get("target_type") or (understanding.entities.get("target_type") if getattr(understanding, "entities", None) else None)
            if reqTType:
                reqTType = str(reqTType).upper().strip()

            for cand in reranked:
                if cand.rerankScore <= 0:
                    continue
                prod = cand.product

                if reqTType in ("SERVICE", "PRODUCT"):
                    pT = getattr(prod, "targetType", getattr(prod, "target_type", "PRODUCT")).upper()
                    if pT != reqTType:
                        continue

                if "Sai lệch ngành hàng" in cand.relevanceExplanation and len(results) >= 1:
                    continue

                baseKey = self.getBaseProductKey(prod.name or "", prod.brand or "")

                if baseKey in seenBaseKeys:
                    continue

                if isRoutine:
                    pCat = (prod.category or "").lower()
                    if pCat in seenRoutineCategories:
                        continue
                    seenRoutineCategories.add(pCat)

                seenBaseKeys.add(baseKey)
                results.append(cand)
                if len(results) >= limit:
                    break

            if not results and reranked:
                results = reranked[:min(1 if limit == 1 else 3, limit)]

        logger.info(
            "Đã tái xếp hạng %d ứng viên thành %d sản phẩm đa dạng trong %.2f ms",
            len(candidates),
            len(results),
            tracker.getTotalDuration(),
        )
        return results


RerankerService = ProductReranker
rerankerService = ProductReranker()
reranker_service = rerankerService
