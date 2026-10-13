import re
from typing import Any, Dict, List, Optional, Set, Tuple


class IngredientSafetyEngine:
    RETINOIDS = {
        "retinol", "tretinoin", "adapalene", "retinal", "retinaldehyde", "hydroxypinacolone retinoate", "tazarotene"
    }
    EXFOLIATING_ACIDS = {
        "bha", "salicylic acid", "aha", "glycolic acid", "lactic acid", "mandelic acid", "pha", "gluconolactone"
    }
    VITAMIN_C_PURE = {
        "ascorbic acid", "l-ascorbic acid", "vitamin c tinh khiết"
    }
    NIACINAMIDE = {
        "niacinamide", "vitamin b3"
    }
    BENZOYL_PEROXIDE = {
        "benzoyl peroxide", "bpo"
    }

    INTERACTION_RULES = [
        {
            "id": "RETINOID_AND_ACID",
            "group_a": RETINOIDS,
            "group_b": EXFOLIATING_ACIDS,
            "name_a": "Retinoids (Retinol/Tretinoin)",
            "name_b": "Acid tẩy tế bào chết (BHA/AHA)",
            "severity": "high",
            "warning": (
                "Lưu ý an toàn da liễu: Sản phẩm chứa Retinoid và BHA/AHA có cơ chế bạt sừng mạnh mẽ. "
                "Không nên thoa cùng lúc trong một buổi để tránh làm tổn thương màng ẩm tự nhiên và gây châm chích đỏ rát. "
                "Khuyến nghị: Dùng xen kẽ cách ngày (ví dụ: BHA vào tối 2-4-6, Retinol vào tối 3-5-7)."
            )
        },
        {
            "id": "VITAMIN_C_AND_NIACINAMIDE",
            "group_a": VITAMIN_C_PURE,
            "group_b": NIACINAMIDE,
            "name_a": "Vitamin C nguyên chất (L-AA)",
            "name_b": "Niacinamide nồng độ cao",
            "severity": "medium",
            "warning": (
                "Mẹo phối hợp da liễu: Vitamin C nguyên chất (L-AA) hoạt động tối ưu ở pH acid thấp (< 3.5), "
                "trong khi Niacinamide tối ưu ở pH trung tính (~6.0). "
                "Khuyến nghị: Thoa cách nhau 15-20 phút, hoặc chia Vitamin C vào buổi sáng (tăng cường bảo vệ chống oxy hóa cùng KCN) "
                "và Niacinamide phục hồi vào buổi tối."
            )
        },
        {
            "id": "BENZOYL_PEROXIDE_AND_RETINOL",
            "group_a": BENZOYL_PEROXIDE,
            "group_b": RETINOIDS,
            "name_a": "Benzoyl Peroxide",
            "name_b": "Retinoids",
            "severity": "medium",
            "warning": (
                "Lưu ý tương tác: Benzoyl Peroxide có tính oxy hóa mạnh, có thể làm bất hoạt phân tử Retinol thông thường nếu thoa đè lên nhau. "
                "Khuyến nghị: Chấm Benzoyl Peroxide vào buổi sáng cho nốt mụn sưng viêm, dùng Retinol tái tạo màng da vào ban đêm."
            )
        },
        {
            "id": "BENZOYL_PEROXIDE_AND_VITAMIN_C",
            "group_a": BENZOYL_PEROXIDE,
            "group_b": VITAMIN_C_PURE,
            "name_a": "Benzoyl Peroxide",
            "name_b": "Vitamin C nguyên chất (L-AA)",
            "severity": "high",
            "warning": (
                "Lưu ý an toàn da liễu: Benzoyl Peroxide là chất oxy hóa mạnh, có thể làm oxy hóa phân tử Vitamin C tinh khiết (L-AA) khiến cả hai giảm tác dụng và dễ gây kích ứng, đỏ rát. "
                "Khuyến nghị: Không nên dùng chung cùng lúc. Hãy thoa Vitamin C vào buổi sáng và Benzoyl Peroxide vào buổi tối hoặc cách nhau nhiều giờ."
            )
        },
    ]

    @classmethod
    def extractActiveGroups(cls, text: str) -> Set[str]:
        tClean = text.lower()
        groups = set()
        for gName, gSet in [
            ("retinoids", cls.RETINOIDS),
            ("exfoliating_acids", cls.EXFOLIATING_ACIDS),
            ("vitamin_c_pure", cls.VITAMIN_C_PURE),
            ("niacinamide", cls.NIACINAMIDE),
            ("benzoyl_peroxide", cls.BENZOYL_PEROXIDE),
        ]:
            for item in gSet:
                if re.search(r"\b" + re.escape(item) + r"\b", tClean):
                    groups.add(gName)
                    break
        return groups

    @classmethod
    def checkInteractionBetweenTexts(cls, textA: str, textB: str, **kwargs) -> List[str]:
        textA = kwargs.get("text_a", textA)
        textB = kwargs.get("text_b", textB)
        groupsA = cls.extractActiveGroups(textA)
        groupsB = cls.extractActiveGroups(textB)
        warnings: List[str] = []

        for rule in cls.INTERACTION_RULES:
            gAName = [k for k, v in [
                ("retinoids", cls.RETINOIDS),
                ("exfoliating_acids", cls.EXFOLIATING_ACIDS),
                ("vitamin_c_pure", cls.VITAMIN_C_PURE),
                ("niacinamide", cls.NIACINAMIDE),
                ("benzoyl_peroxide", cls.BENZOYL_PEROXIDE),
            ] if v == rule["group_a"]][0]

            gBName = [k for k, v in [
                ("retinoids", cls.RETINOIDS),
                ("exfoliating_acids", cls.EXFOLIATING_ACIDS),
                ("vitamin_c_pure", cls.VITAMIN_C_PURE),
                ("niacinamide", cls.NIACINAMIDE),
                ("benzoyl_peroxide", cls.BENZOYL_PEROXIDE),
            ] if v == rule["group_b"]][0]

            if (gAName in groupsA and gBName in groupsB) or (gBName in groupsA and gAName in groupsB):
                warnings.append(rule["warning"])

        return warnings

    @classmethod
    def checkCandidatesSafety(cls, candidates: List[Any], userQuery: str = "", **kwargs) -> List[str]:
        userQuery = kwargs.get("user_query", userQuery)
        warnings: List[str] = []
        seenRules = set()

        if userQuery:
            for cand in candidates:
                prod = getattr(cand, "product", cand)
                ing = getattr(prod, "ingredients", "") or ""
                wList = cls.checkInteractionBetweenTexts(userQuery, ing)
                for w in wList:
                    if w not in seenRules:
                        seenRules.add(w)
                        warnings.append(w)

        prods = [getattr(c, "product", c) for c in candidates]
        for i in range(len(prods)):
            for j in range(i + 1, len(prods)):
                ingA = getattr(prods[i], "ingredients", "") or ""
                ingB = getattr(prods[j], "ingredients", "") or ""
                wList = cls.checkInteractionBetweenTexts(ingA, ingB)
                for w in wList:
                    if w not in seenRules:
                        seenRules.add(w)
                        warnings.append(w)

        hasPhotosensitizing = any(
            any(g in cls.extractActiveGroups(getattr(p, "ingredients", "") or "") for g in ["retinoids", "exfoliating_acids"])
            for p in prods
        )
        hasSunscreenInRec = any(
            (getattr(p, "category", "") or "").lower() == "sunscreen" or "chống nắng" in (getattr(p, "name", "") or "").lower()
            for p in prods
        )
        if hasPhotosensitizing and not hasSunscreenInRec:
            sunTip = (
                "Lưu ý bảo vệ tia UV: Do chu trình chứa hoạt chất tái tạo da (Retinoid / Acid bạt sừng), "
                "làn da sẽ nhạy cảm hơn trước ánh nắng mặt trời. Bắt buộc phải thoa kem chống nắng phổ rộng có chỉ số SPF 30+ trở lên vào mỗi sáng."
            )
            if sunTip not in seenRules:
                seenRules.add(sunTip)
                warnings.append(sunTip)

        return warnings

    _extract_active_groups = extractActiveGroups
    check_interaction_between_texts = checkInteractionBetweenTexts
    check_candidates_safety = checkCandidatesSafety


ingredientSafetyEngine = IngredientSafetyEngine()
ingredient_safety_engine = ingredientSafetyEngine
