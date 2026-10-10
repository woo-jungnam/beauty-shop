"""Kịch bản kiểm thử thuật toán Content-Based RecSys:
So sánh danh sách gợi ý trước và sau khi thêm hành vi người dùng (User Interactions).

Cách chạy:
    cd chatbot/product-agent
    python test_recsys_behavior.py
"""
import sys
from pathlib import Path

# Thêm thư mục gốc vào PYTHONPATH
sys.path.insert(0, str(Path(__file__).resolve().parent))

from app.services.recommendation import recommendationService, ACTION_WEIGHTS
from app.services.ingestion.extractor import DatabaseExtractor


def run_test():
    print("====================================================================")
    print(" KIỂM THỬ THUẬT TOÁN GỢI Ý CÁ NHÂN HÓA (CONTENT-BASED RECSYS)")
    print("====================================================================")

    db_extractor = DatabaseExtractor()
    test_user_id = 999999  # Giả lập user_id test để không làm nhiễu dữ liệu thật
    test_session_id = "test_behavior_session"

    conn = db_extractor.getConnection()
    try:
        with conn.cursor() as cur:
            # 1. Dọn sạch tương tác cũ của user test nếu có
            cur.execute("DELETE FROM user_product_interactions WHERE user_id = %s OR session_id = %s", (test_user_id, test_session_id))

            # Lấy 2 sản phẩm mẫu từ DB để làm dữ liệu tương tác
            cur.execute("""
                SELECT id, name, category, short_description
                FROM products
                WHERE is_deleted = 0 AND status = 'ACTIVE'
                ORDER BY id ASC LIMIT 2
            """)
            sample_products = cur.fetchall()

        if not sample_products or len(sample_products) < 2:
            print("[CẢNH BÁO] Database chưa có đủ sản phẩm hoạt động để kiểm thử.")
            return

        prod_a = sample_products[0]
        prod_b = sample_products[1]
        print(f"\n[Dữ liệu mẫu chọn để tương tác]:")
        print(f"  - Sản phẩm 1 (ID={prod_a['id']}): {prod_a['name']}")
        print(f"  - Sản phẩm 2 (ID={prod_b['id']}): {prod_b['name']}")

        # -------------------------------------------------------------
        # BƯỚC 1: GỢI Ý KHI CHƯA CÓ HÀNH VI (COLD-START BASELINE)
        # -------------------------------------------------------------
        print("\n--- BƯỚC 1: Lấy gợi ý khi CHƯA có hành vi tương tác ---")
        baseline_ids = recommendationService.getRecommendationsForUser(userId=test_user_id, limit=6)
        print(f"-> Thuật toán áp dụng: Cold-Start Fallback (Sản phẩm bán chạy/rating cao)")
        print(f"-> Danh sách gợi ý ban đầu (IDs): {baseline_ids}")

        # -------------------------------------------------------------
        # BƯỚC 2: THÊM HÀNH VI TƯƠNG TÁC CHO USER
        # -------------------------------------------------------------
        print("\n--- BƯỚC 2: Giả lập ghi nhận hành vi tương tác ---")
        with conn.cursor() as cur:
            # Tương tác VIEW cho sản phẩm A (trọng số 1.0)
            cur.execute("""
                INSERT INTO user_product_interactions (user_id, session_id, product_id, action_type)
                VALUES (%s, %s, %s, 'VIEW')
            """, (test_user_id, test_session_id, prod_a['id']))

            # Tương tác ADD_TO_CART cho sản phẩm B (trọng số 3.0)
            cur.execute("""
                INSERT INTO user_product_interactions (user_id, session_id, product_id, action_type)
                VALUES (%s, %s, %s, 'ADD_TO_CART')
            """, (test_user_id, test_session_id, prod_b['id']))

        print(f"-> Đã ghi nhận: VIEW sản phẩm ID={prod_a['id']} (Trọng số: {ACTION_WEIGHTS.get('VIEW')})")
        print(f"-> Đã ghi nhận: ADD_TO_CART sản phẩm ID={prod_b['id']} (Trọng số: {ACTION_WEIGHTS.get('ADD_TO_CART')})")

        # -------------------------------------------------------------
        # BƯỚC 3: LẤY GỢI Ý SAU KHI CÓ HÀNH VI (USER PROFILE CENTROID)
        # -------------------------------------------------------------
        print("\n--- BƯỚC 3: Lấy gợi ý SAU KHI đã có lịch sử hành vi ---")
        personalized_ids = recommendationService.getRecommendationsForUser(userId=test_user_id, limit=6)
        print(f"-> Thuật toán áp dụng: Content-Based User-to-Item (Weighted Centroid Cosine)")
        print(f"-> Danh sách gợi ý mới (IDs): {personalized_ids}")

        # -------------------------------------------------------------
        # BƯỚC 4: ĐÁNH GIÁ ĐỘ LỆCH VÀ TÍNH CHẤT GỢI Ý
        # -------------------------------------------------------------
        print("\n--- BƯỚC 4: Đánh giá kết quả ---")
        print(f"1. Danh sách ban đầu (Cold-Start): {baseline_ids}")
        print(f"2. Danh sách cá nhân hóa mới:    {personalized_ids}")

        # Kiểm tra tính loại trừ sản phẩm đã tương tác
        has_interacted_a = prod_a['id'] in personalized_ids
        has_interacted_b = prod_b['id'] in personalized_ids
        print(f"3. Lọc trùng: Đã loại trừ sản phẩm user vừa xem/mua? {'CHÍNH XÁC (True)' if (not has_interacted_a and not has_interacted_b) else 'Chưa loại trừ (False)'}")

        # Kiểm tra sự khác biệt giữa 2 danh sách
        is_different = baseline_ids != personalized_ids
        print(f"4. Sự khác biệt giữa 2 danh sách: {'KHÁC BIỆT RÕ RỆT (Thuật toán đã phản hồi theo hành vi)' if is_different else 'GIỐNG NHAU (Đang fallback)'}")

        # In tên sản phẩm được gợi ý mới
        if personalized_ids:
            with conn.cursor() as cur:
                format_strings = ','.join(['%s'] * len(personalized_ids))
                cur.execute(f"SELECT id, name FROM products WHERE id IN ({format_strings})", tuple(personalized_ids))
                recs = cur.fetchall()
            print("\n[Chi tiết các sản phẩm được hệ thống gợi ý cho bạn]:")
            for r in recs:
                print(f"  * [ID={r['id']}] {r['name']}")

    finally:
        # Dọn dẹp dữ liệu test để tránh rác DB
        with conn.cursor() as cur:
            cur.execute("DELETE FROM user_product_interactions WHERE user_id = %s OR session_id = %s", (test_user_id, test_session_id))
        conn.close()
        print("\n[Dọn dẹp]: Đã xóa dữ liệu tương tác test thành công.")


if __name__ == "__main__":
    run_test()
