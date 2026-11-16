-- Seed initial banners for homepage sections with 5:3 aspect ratio and admin toggle ability
INSERT INTO banners (title, badge, description, image_url, target_url, cta_text, position, sort_order, is_active, created_by)
VALUES
(
    'Flash Sale Giờ Vàng - Săn Deal Giá Sốc Đến 50%',
    'FLASH SALE',
    'Ưu đãi độc quyền mỹ phẩm chính hãng với số lượng có hạn mỗi ngày.',
    'https://images.unsplash.com/photo-1608248597359-5433010b98f2?auto=format&fit=crop&w=1000&h=600&q=80',
    '#flash-sale',
    'Săn Deal Ngay',
    'SECTION_FLASH_SALE',
    1,
    TRUE,
    'system'
),
(
    'Khám Phá Danh Mục Mỹ Phẩm & Chăm Sóc Da Toàn Diện',
    'DANH MỤC NỔI BẬT',
    'Tìm kiếm sản phẩm phù hợp từng tình trạng da từ các danh mục tuyển chọn.',
    'https://images.unsplash.com/photo-1598440947619-2c35fc9aa908?auto=format&fit=crop&w=1000&h=600&q=80',
    '#categories-section',
    'Khám Phá Ngay',
    'SECTION_CATEGORIES',
    1,
    TRUE,
    'system'
),
(
    'Thương Hiệu Quốc Tế Chính Hãng 100% - Phân Phối Độc Quyền',
    'THƯƠNG HIỆU CHÍNH HÃNG',
    'Cam kết 100% hàng thật có tem phụ và chứng nhận phân phối chính hãng.',
    'https://images.unsplash.com/photo-1522337360788-8b13dee7a37e?auto=format&fit=crop&w=1000&h=600&q=80',
    '#brand-partners',
    'Xem Thương Hiệu',
    'SECTION_BRANDS',
    1,
    TRUE,
    'system'
),
(
    'Gợi Ý Chăm Sóc Da Cá Nhân Hóa Dành Riêng Cho Bạn',
    'GỢI Ý THÔNG MINH',
    'Routine khoa học được đề xuất dựa trên hoạt chất và nhu cầu phục hồi làn da của bạn.',
    'https://images.unsplash.com/photo-1570172619644-dfd03ed5d881?auto=format&fit=crop&w=1000&h=600&q=80',
    '#random-picks',
    'Xem Gợi Ý',
    'SECTION_RECOMMENDATIONS',
    1,
    TRUE,
    'system'
),
(
    'Bộ Sưu Tập Sản Phẩm Làm Đẹp Chuẩn Dược Mỹ Phẩm',
    'TẤT CẢ SẢN PHẨM',
    'Đầy đủ các dòng sản phẩm từ làm sạch, dưỡng ẩm đến đặc trị chuyên sâu.',
    'https://images.unsplash.com/photo-1512290900672-1f551b9426d4?auto=format&fit=crop&w=1000&h=600&q=80',
    '#store-catalog',
    'Xem Tất Cả',
    'SECTION_CATALOG',
    1,
    TRUE,
    'system'
),
(
    'Liệu Trình Spa Thư Giãn & Phục Hồi Chuyên Sâu Chuẩn Y Khoa',
    'DỊCH VỤ SPA',
    'Trải nghiệm công nghệ Aqua Peel, điện di vi điểm và chăm sóc da đa tầng cùng chuyên viên.',
    'https://images.unsplash.com/photo-1540555700478-4be289fbecef?auto=format&fit=crop&w=1000&h=600&q=80',
    'booking',
    'Đặt Lịch Spa Ngay',
    'SECTION_SPA',
    1,
    TRUE,
    'system'
);
