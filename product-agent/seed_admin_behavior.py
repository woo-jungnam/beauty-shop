"""Nạp dữ liệu hành vi thực tế cho tài khoản Admin (user_id = 1) vào MySQL.
Sau đó chạy thuật toán RecSys để xem trước danh sách sản phẩm được cá nhân hóa cho Admin.

Cách dùng:
    cd chatbot/product-agent
    python seed_admin_behavior.py
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from app.services.recommendation import recommendationService, ACTION_WEIGHTS
from app.services.ingestion.extractor import DatabaseExtractor


def seed_admin_interactions():
    print("====================================================================")
    print(" NẠP DỮ LIỆU HÀNH VI CHO TÀI KHOẢN ADMIN (USER_ID = 1)")
    print("====================================================================")

    db_extractor = DatabaseExtractor()
    conn = db_extractor.getConnection()
    admin_user_id = 1

    try:
        with conn.cursor() as cur:
            # 1. Kiểm tra tài khoản admin trong bảng users
            cur.execute("SELECT id, email, full_name, username FROM users WHERE id = %s", (admin_user_id,))
            admin_user = cur.fetchone()
            if not admin_user:
                print(f"[LỖI] Không tìm thấy user với id = {admin_user_id} trong database.")
                return

            print(f"-> Tài khoản mục tiêu: ID={admin_user['id']} | Email: {admin_user['email']} | Tên: {admin_user['full_name']}")

            # 2. Xóa các hành vi cũ của admin (nếu có) để tạo hành vi mới rõ rệt
            cur.execute("DELETE FROM user_product_interactions WHERE user_id = %s", (admin_user_id,))
            print(f"-> Đã làm sạch lịch sử tương tác cũ của Admin.")

            # 3. Chọn chủ đề hành vi: Nhóm Kem Chống Nắng & Bảo Vệ Da (Sunscreen & UV Protection)
            # Lấy 2 sản phẩm chống nắng tiêu biểu để tương tác
            cur.execute("""
                SELECT id, name, category, short_description
                FROM products
                WHERE is_deleted = 0 AND status = 'ACTIVE'
                  AND (name LIKE '%Chống Nắng%' OR category LIKE '%chống nắng%' OR short_description LIKE '%chống nắng%')
                ORDER BY id ASC LIMIT 3
            """)
            sunscreen_products = cur.fetchall()

            if not sunscreen_products or len(sunscreen_products) < 2:
                # Fallback nếu không tìm thấy keyword chống nắng
                cur.execute("""
                    SELECT id, name, category, short_description
                    FROM products
                    WHERE is_deleted = 0 AND status = 'ACTIVE'
                    ORDER BY id ASC LIMIT 3
                """)
                sunscreen_products = cur.fetchall()

            prod_view = sunscreen_products[0]
            prod_cart = sunscreen_products[1]

            # 4. Ghi nhận hành vi vào user_product_interactions
            # Hành vi 1: VIEW
            cur.execute("""
                INSERT INTO user_product_interactions (user_id, session_id, product_id, action_type)
                VALUES (%s, NULL, %s, 'VIEW')
            """, (admin_user_id, prod_view['id']))

            # Hành vi 2: ADD_TO_CART (trọng số cao x3)
            cur.execute("""
                INSERT INTO user_product_interactions (user_id, session_id, product_id, action_type)
                VALUES (%s, NULL, %s, 'ADD_TO_CART')
            """, (admin_user_id, prod_cart['id']))

            print(f"\n[Đã nạp 2 hành vi quan tâm đặc biệt vào Database]:")
            print(f"  1. VIEW         -> [ID: {prod_view['id']}] {prod_view['name']}")
            print(f"  2. ADD_TO_CART  -> [ID: {prod_cart['id']}] {prod_cart['name']}")

        # 5. Gọi thuật toán gợi ý xem trước kết quả
        print("\n--- CHẠY THỬ THUẬT TOÁN GỢI Ý CÁ NHÂN HÓA CHO ADMIN ---")
        recommended_ids = recommendationService.getRecommendationsForUser(userId=admin_user_id, limit=8)
        print(f"-> Danh sách Product IDs được thuật toán đề xuất: {recommended_ids}")

        if recommended_ids:
            with conn.cursor() as cur:
                format_strings = ','.join(['%s'] * len(recommended_ids))
                cur.execute(f"""
                    SELECT p.id, p.name, COALESCE(b.name, '') as brand, CAST(p.base_price AS DOUBLE) as price
                    FROM products p
                    LEFT JOIN brands b ON p.brand_id = b.id
                    WHERE p.id IN ({format_strings})
                """, tuple(recommended_ids))
                recs = cur.fetchall()

            print("\n[CHI TIẾT DANH SÁCH 'GỢI Ý CHO BẠN' MÀ ADMIN SẼ NHÌN THẤY TRÊN HOMEPAGE]:")
            for idx, r in enumerate(recs, 1):
                print(f"  {idx}. [ID={r['id']}] {r['name']} - Thương hiệu: {r['brand']} - Giá: {r['price']:,.0f} đ")

            # Đảm bảo không trùng 2 sản phẩm admin đã tương tác
            interacted_ids = {prod_view['id'], prod_cart['id']}
            intersect = set(recommended_ids).intersection(interacted_ids)
            if not intersect:
                print("\n[ĐÁNH GIÁ THUẬT TOÁN]: Hoàn hảo! Đã loại bỏ các sản phẩm Admin vừa xem/thêm giỏ, ưu tiên các sản phẩm tương tự cùng danh mục & công dụng.")
            else:
                print(f"\n[LƯU Ý]: Có trùng {intersect} sản phẩm đã xem.")

        print("\n====================================================================")
        print("HOÀN TẤT! Giờ bạn có thể đăng nhập tài khoản Admin vào trang chủ để thấy sự khác biệt.")
        print("Tài khoản: namnt4560@gmail.com | Mật khẩu: admin123")
        print("====================================================================")

    finally:
        conn.close()


if __name__ == "__main__":
    seed_admin_interactions()
