import asyncio
import json
from app.schemas.query import UnderstandingResult, Intent, PrimaryIntent, ActionRouting

def test_cases():
    print("=== KIỂM THỬ KHUNG JSON NLU, TARGET KHÔNG NULL VÀ INTENT XỬ LÝ ===")
    
    test_queries = [
        ("serum", "PRODUCT", True, PrimaryIntent.KHONG_XAC_DINH.value, Intent.UNKNOWN),
        ("dịch vụ chăm sóc", "SERVICE", True, PrimaryIntent.KHONG_XAC_DINH.value, Intent.UNKNOWN),
        ("kem dưỡng", "PRODUCT", True, PrimaryIntent.KHONG_XAC_DINH.value, Intent.UNKNOWN),
        ("chăm sóc da", "SERVICE", True, PrimaryIntent.KHONG_XAC_DINH.value, Intent.UNKNOWN),
        ("Có loại kem chống nắng nào bôi lên mát da thấm nhanh không?", "PRODUCT", False, PrimaryIntent.TU_VAN_SAN_PHAM.value, Intent.PRODUCT_SEARCH),
        ("Tìm serum cấp ẩm phục hồi da dầu mụn", "PRODUCT", False, PrimaryIntent.TU_VAN_SAN_PHAM.value, Intent.PRODUCT_SEARCH),
        ("Bảng giá triệt lông nách bên mình sao ạ?", "SERVICE", False, PrimaryIntent.GIA_NGAN_SACH.value, Intent.PRICE_QUERY),
        ("Đặt lịch peel da chiều mai", "SERVICE", False, PrimaryIntent.DAT_LICH.value, Intent.BOOKING),
        ("xin chào shop", "PRODUCT", False, PrimaryIntent.CHAO_HOI.value, Intent.GENERAL_CHAT),
        ("1+1", "PRODUCT", False, PrimaryIntent.NGOAI_PHAM_VI.value, Intent.OUT_OF_DOMAIN),
        ("1 + 1 = ?", "PRODUCT", False, PrimaryIntent.NGOAI_PHAM_VI.value, Intent.OUT_OF_DOMAIN),
    ]

    for q, exp_target, exp_clarify, exp_p_intent, exp_intent in test_queries:
        data = {
            "raw_query": q,
            "target_type": None,  # Thử nghiệm input null để kiểm tra chống null
            "target": None,
            "primary_intent": exp_p_intent if not exp_clarify and exp_p_intent != PrimaryIntent.KHONG_XAC_DINH.value else None,
            "intent": exp_intent if not exp_clarify and exp_intent != Intent.UNKNOWN else None,
        }
        res = UnderstandingResult.model_validate(data)
        
        target_not_null = res.target_type is not None and res.target_type != ""
        target_match = res.target_type == exp_target
        clarify_match = res.needs_clarification == exp_clarify
        intent_match = (
            (res.primary_intent == exp_p_intent or (exp_clarify and res.primary_intent == PrimaryIntent.KHONG_XAC_DINH.value))
            and (res.intent == exp_intent or (exp_clarify and res.intent == Intent.UNKNOWN))
        )

        status = "PASSED" if (target_not_null and target_match and clarify_match and intent_match) else "FAILED"
        print(f"[{status}] Query: '{q}'")
        print(f"   -> Target: {res.target_type} (Non-null: {target_not_null}, Expected: {exp_target})")
        print(f"   -> Primary Intent: {res.primary_intent} | Intent: {res.intent}")
        print(f"   -> Info Score: {res.information_score} | Needs Clarify: {res.needs_clarification}")
        print(f"   -> Action: {res.action_routing}")
        if res.clarification_question:
            print(f"   -> Clarification Question: {res.clarification_question}")
        print("-" * 70)

        assert target_not_null, f"Target bị null trong câu '{q}'"
        assert target_match, f"Target không khớp: thực tế {res.target_type}, kỳ vọng {exp_target}"
        assert clarify_match, f"Clarify không khớp: thực tế {res.needs_clarification}, kỳ vọng {exp_clarify}"

    # Kiểm tra đồng bộ hóa Entities 2 chiều từ Flat/Constraints
    sync_test_data = {
        "raw_query": "tìm serum b5 la roche-posay cho da dầu dưới 500k trị thâm mụn",
        "category": "serum",
        "brand": "La Roche-Posay",
        "skin_type": "oily",
        "concerns": ["mụn", "thâm"],
        "ingredients": ["b5"],
        "max_price": 500000,
    }
    sync_res = UnderstandingResult.model_validate(sync_test_data)
    assert sync_res.entities.get("product_category") == "serum"
    assert sync_res.entities.get("brands") == ["La Roche-Posay"]
    assert sync_res.entities.get("skin_type") == "oily"
    assert sync_res.entities.get("target_concerns") == ["mụn", "thâm"]
    assert sync_res.entities.get("ingredients_mentioned") == ["b5"]
    assert sync_res.entities.get("price_mentioned") == 500000.0

    print("Tất cả các ca kiểm thử unit logic đều THÀNH CÔNG VƯỢT TRỘI!")

if __name__ == "__main__":
    test_cases()
