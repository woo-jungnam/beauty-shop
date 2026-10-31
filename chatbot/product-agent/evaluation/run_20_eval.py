import json
import time
import urllib.request
import sys
import io

# Fix Windows console UTF-8 output
if hasattr(sys.stdout, "buffer"):
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "buffer"):
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

BASE_URL = "http://127.0.0.1:18000"

# 20 Testcases: 10 Sản phẩm + 10 Dịch vụ Spa (Bao gồm câu hỏi thông thường, câu hỏi có typo, câu hỏi cụ thể)
TEST_CASES = [
    # --- PHẦN 1: DỊCH VỤ SPA & CLINIC (10 CA) ---
    {
        "id": "S1",
        "category": "SPA",
        "query": "tư vấn dịch về chăm sóc da mặt bị rổ do nặn mun",
        "expected_target": "SERVICE",
        "expected_domain": ["mụn", "sẹo rỗ", "lấy nhân mụn", "tái sinh da", "chăm sóc da"],
        "unexpected_domain": ["massage", "tắm trắng", "body", "thực phẩm chức năng"]
    },
    {
        "id": "S2",
        "category": "SPA",
        "query": "Bên mình có dịch vụ nặn mụn y khoa không, chi phí bao nhiêu?",
        "expected_target": "SERVICE",
        "expected_domain": ["Liệu Trình Điều Trị Mụn Chuẩn Y Khoa", "mụn"],
        "unexpected_domain": ["massage", "tắm trắng"]
    },
    {
        "id": "S3",
        "category": "SPA",
        "query": "Da bị thâm nám tàn nhang nên làm liệu trình nào của spa?",
        "expected_target": "SERVICE",
        "expected_domain": ["Laser Toning", "thâm nám", "tàn nhang"],
        "unexpected_domain": ["tắm trắng", "massage"]
    },
    {
        "id": "S4",
        "category": "SPA",
        "query": "Mình muốn làm sạch sâu bã nhờn và hút mụn cám ở spa",
        "expected_target": "SERVICE",
        "expected_domain": ["Aqua Peel", "làm sạch sâu", "mụn cám"],
        "unexpected_domain": ["massage", "nâng cơ Hifu"]
    },
    {
        "id": "S5",
        "category": "SPA",
        "query": "Liệu trình trẻ hóa nâng cơ mặt Hifu giá bao nhiêu và thời gian bao lâu?",
        "expected_target": "SERVICE",
        "expected_domain": ["Hifu Ultra V-Line", "nâng cơ"],
        "unexpected_domain": ["tắm trắng", "massage"]
    },
    {
        "id": "S6",
        "category": "SPA",
        "query": "Điện di vitamin C giúp da sáng căng bóng bao nhiêu tiền một buổi?",
        "expected_target": "SERVICE",
        "expected_domain": ["Điện Di Tinh Chất Vitamin C", "căng bóng"],
        "unexpected_domain": ["Hifu", "massage"]
    },
    {
        "id": "S7",
        "category": "SPA",
        "query": "Spa có gói cấy collagen tươi phục hồi da khô lão hóa không?",
        "expected_target": "SERVICE",
        "expected_domain": ["Cấy Tinh Chất Collagen Tươi", "phục hồi"],
        "unexpected_domain": ["nặn mụn", "tắm trắng"]
    },
    {
        "id": "S8",
        "category": "SPA",
        "query": "Dịch vụ massage body thư giãn đá nóng bên bạn giá thế nào?",
        "expected_target": "SERVICE",
        "expected_domain": ["Massage Toàn Thân Đá Nóng", "thảo dược"],
        "unexpected_domain": ["Aqua Peel", "Laser Toning"]
    },
    {
        "id": "S9",
        "category": "SPA",
        "query": "Tắm trắng phi thuyền toàn thân bên shop có hiệu quả không, giá bao nhiêu?",
        "expected_target": "SERVICE",
        "expected_domain": ["Tắm Trắng Toàn Thân Công Nghệ Phi Thuyền", "phi thuyền"],
        "unexpected_domain": ["mụn", "nâng cơ"]
    },
    {
        "id": "S10",
        "category": "SPA",
        "query": "Tôi muốn đặt lịch soi da và chăm sóc da mặt cuối tuần này",
        "expected_target": "SERVICE",
        "expected_domain": ["Aqua Peel", "chăm sóc da", "soi da", "lịch hẹn"],
        "unexpected_domain": ["tắm trắng phi thuyền"]
    },

    # --- PHẦN 2: SẢN PHẨM MỸ PHẨM E-COMMERCE (10 CA) ---
    {
        "id": "P1",
        "category": "PRODUCT",
        "query": "Tư vấn kem chống nắng cho da dầu mụn không nâng tone kiềm dầu tốt",
        "expected_target": "PRODUCT",
        "expected_domain": ["chống nắng", "kiềm dầu", "da dầu", "da mụn"],
        "unexpected_domain": ["sữa tắm", "dưỡng thể", "son môi"]
    },
    {
        "id": "P2",
        "category": "PRODUCT",
        "query": "Kem dưỡng phục hồi La Roche-Posay B5 Cicaplast giá bao nhiêu?",
        "expected_target": "PRODUCT",
        "expected_domain": ["La Roche-Posay", "Cicaplast", "B5"],
        "unexpected_domain": ["kem chống nắng", "nước hoa"]
    },
    {
        "id": "P3",
        "category": "PRODUCT",
        "query": "Serum cấp ẩm phục hồi da nhạy cảm dưới 500k",
        "expected_target": "PRODUCT",
        "expected_domain": ["serum", "tinh chất", "cấp ẩm", "phục hồi"],
        "unexpected_domain": ["kem chống nắng", "son"]
    },
    {
        "id": "P4",
        "category": "PRODUCT",
        "query": "Sữa rửa mặt tạo bọt dịu nhẹ cho da nhạy cảm CeraVe",
        "expected_target": "PRODUCT",
        "expected_domain": ["sữa rửa mặt", "CeraVe", "làm sạch"],
        "unexpected_domain": ["kem nền", "tẩy trang"]
    },
    {
        "id": "P5",
        "category": "PRODUCT",
        "query": "Nước hoa hồng toner Some By Mi trị mụn có sẵn không shop?",
        "expected_target": "PRODUCT",
        "expected_domain": ["Some By Mi", "Toner", "AHA-BHA-PHA"],
        "unexpected_domain": ["kem chống nắng", "dưỡng thể"]
    },
    {
        "id": "P6",
        "category": "PRODUCT",
        "query": "Tẩy tế bào chết BHA COSRX dùng cho mụn đầu đen có tốt không?",
        "expected_target": "PRODUCT",
        "expected_domain": ["COSRX", "BHA", "mụn đầu đen"],
        "unexpected_domain": ["sữa tắm", "phấn phủ"]
    },
    {
        "id": "P7",
        "category": "PRODUCT",
        "query": "Có loại nước tẩy trang Micellar nào sạch sâu không cồn không hương liệu không?",
        "expected_target": "PRODUCT",
        "expected_domain": ["tẩy trang", "không cồn", "Micellar"],
        "unexpected_domain": ["kem mắt", "son môi"]
    },
    {
        "id": "P8",
        "category": "PRODUCT",
        "query": "Serum Niacinamide giảm thâm mụn kiềm dầu của The Ordinary hoặc COSRX",
        "expected_target": "PRODUCT",
        "expected_domain": ["Niacinamide", "thâm mụn", "serum"],
        "unexpected_domain": ["kem chống nắng", "son dưỡng"]
    },
    {
        "id": "P9",
        "category": "PRODUCT",
        "query": "Kem dưỡng ẩm cho da khô bong tróc vào mùa đông",
        "expected_target": "PRODUCT",
        "expected_domain": ["kem dưỡng", "cấp ẩm", "da khô", "ẩm mượt"],
        "unexpected_domain": ["kiềm dầu", "mụn mủ"]
    },
    {
        "id": "P10",
        "category": "PRODUCT",
        "query": "Son dưỡng môi có màu hoặc không màu chống khô nứt nẻ",
        "expected_target": "PRODUCT",
        "expected_domain": ["son", "dưỡng môi", "môi"],
        "unexpected_domain": ["sữa rửa mặt", "kem chống nắng body"]
    }
]

def query_chat(message: str, session_id: str):
    url = f"{BASE_URL}/chat"
    payload = {"message": message, "session_id": session_id}
    req = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"}
    )
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=45) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            dur = time.time() - t0
            return data, dur, None
    except Exception as e:
        dur = time.time() - t0
        return None, dur, str(e)

def main():
    print("=" * 80)
    print(" BÀI TEST TOÀN DIỆN 20 CÂU HỎI VỀ SẢN PHẨM & DỊCH VỤ SPA")
    print("=" * 80)

    results = []

    for idx, tc in enumerate(TEST_CASES, 1):
        q_id = tc["id"]
        cat = tc["category"]
        query = tc["query"]
        exp_target = tc["expected_target"]

        sess = f"eval_20_{q_id}_{int(time.time())}"
        print(f"\n[{idx}/20] Testcase {q_id} [{cat}]: \"{query}\"")

        data, dur, err = query_chat(query, sess)

        if err:
            print(f"  -> LỖI GỌI API: {err}")
            results.append({
                "id": q_id,
                "category": cat,
                "query": query,
                "error": err,
                "is_accurate": False,
                "latency_s": dur
            })
            continue

        und = data.get("understanding") or {}
        actual_target = und.get("target_type") or und.get("target") or "PRODUCT"
        answer = data.get("answer") or data.get("message") or ""
        products = data.get("products") or []
        product_names = [p.get("name", "") for p in products]

        # Kiểm tra tính chính xác:
        # 1. Target Type phải khớp (PRODUCT / SERVICE)
        target_match = (actual_target == exp_target)

        # 2. Các thẻ trả về phải chứa từ khóa mong đợi và KHÔNG chứa từ khóa cấm kỵ (irrelevant)
        all_text_check = (answer + " " + " ".join(product_names)).lower()

        has_expected = any(kw.lower() in all_text_check for kw in tc["expected_domain"])
        has_unexpected = any(kw.lower() in " ".join(product_names).lower() for kw in tc["unexpected_domain"])

        # Nếu là câu rỗ mụn mà trả về tắm trắng hoặc massage body thì chắc chắn không liên quan
        is_accurate = target_match and has_expected and not has_unexpected

        print(f"  -> Target: {actual_target} (Kỳ vọng: {exp_target}) - Match: {target_match}")
        print(f"  -> Trả về {len(products)} thẻ: {product_names[:3]}")
        print(f"  -> Có chứa chủ đề kỳ vọng: {has_expected} | Chứa dịch vụ lạc đề: {has_unexpected}")
        print(f"  -> Đánh giá chính xác: {'[ĐẠT]' if is_accurate else '[KHÔNG ĐẠT]'}")
        print(f"  -> Trích câu trả lời: {answer[:130]}...")

        results.append({
            "id": q_id,
            "category": cat,
            "query": query,
            "expected_target": exp_target,
            "actual_target": actual_target,
            "product_names": product_names,
            "answer_preview": answer[:150],
            "target_match": target_match,
            "has_expected": has_expected,
            "has_unexpected": has_unexpected,
            "is_accurate": is_accurate,
            "latency_s": round(dur, 2)
        })

        time.sleep(1.0)

    # Tổng kết
    print("\n" + "=" * 80)
    print(" BẢNG TỔNG KẾT ĐÁNH GIÁ ĐỘ CHÍNH XÁC")
    print("=" * 80)
    total = len(results)
    passed = sum(1 for r in results if r.get("is_accurate"))
    spa_total = sum(1 for r in results if r.get("category") == "SPA")
    spa_passed = sum(1 for r in results if r.get("category") == "SPA" and r.get("is_accurate"))
    prod_total = sum(1 for r in results if r.get("category") == "PRODUCT")
    prod_passed = sum(1 for r in results if r.get("category") == "PRODUCT" and r.get("is_accurate"))

    print(f"- Tổng số ca: {total}")
    print(f"- Đạt chuẩn chính xác: {passed}/{total} ({passed/total*100:.1f}%)")
    print(f"- Dịch vụ Spa: {spa_passed}/{spa_total} ({spa_passed/spa_total*100:.1f}%)")
    print(f"- Sản phẩm: {prod_passed}/{prod_total} ({prod_passed/prod_total*100:.1f}%)")

    # Lưu ra file JSON để phân tích chi tiết
    with open("chatbot/product-agent/evaluation/test_20_cases_results.json", "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print("-> Đã lưu kết quả chi tiết ra: chatbot/product-agent/evaluation/test_20_cases_results.json")

if __name__ == "__main__":
    main()
