-- =========================================================================
-- V22: SEED SPA & CLINIC SERVICES, CATEGORIES, PACKAGES, STAFF & FACILITIES
-- =========================================================================

-- 1. Facilities (Chi nhánh Spa & Clinic)
INSERT INTO facilities (id, name, description, is_active, created_by, is_deleted)
VALUES 
(1, 'Beauty Shop Premium Clinic & Spa - Quận 1', '123 Đồng Khởi, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh. Hotline: 1900 6868.', 1, 'system', 0),
(2, 'Beauty Shop Wellness & Skincare - Cầu Giấy', '68 Cầu Giấy, Phường Quan Hoa, Quận Cầu Giấy, Hà Nội. Hotline: 1900 6869.', 1, 'system', 0)
ON DUPLICATE KEY UPDATE name=VALUES(name), description=VALUES(description);

-- 2. Service Categories (Danh mục dịch vụ Spa)
INSERT INTO service_categories (id, name, slug, description, thumbnail_url, is_active, created_by, is_deleted)
VALUES
(1, 'Chăm Sóc Da Chuyên Sâu', 'cham-soc-da-chuyen-sau', 'Làm sạch sâu bã nhờn, hút mụn cám, cấp ẩm tầng sâu và phục hồi hàng rào bảo vệ da chuẩn y khoa.', 'https://images.unsplash.com/photo-1570172619644-dfd03ed5d881?auto=format&fit=crop&w=800&q=80', 1, 'system', 0),
(2, 'Điều Trị Da Chuyên Khoa', 'dieu-tri-da-chuyen-khoa', 'Phác đồ cá nhân hóa điều trị mụn viêm, thâm đỏ, sẹo rỗ và sắc tố tàn nhang bởi Bác sĩ Da liễu.', 'https://images.unsplash.com/photo-1512290900672-1f4142f1f008?auto=format&fit=crop&w=800&q=80', 1, 'system', 0),
(3, 'Trẻ Hóa & Nâng Cơ Công Nghệ Cao', 'tre-hoa-nang-co', 'Ứng dụng sóng siêu âm hội tụ HIFU, Bio-Cell Laser và vi điểm tinh chất giúp tái sinh đàn hồi da không xâm lấn.', 'https://images.unsplash.com/photo-1522337360788-8b13dee7a37e?auto=format&fit=crop&w=800&q=80', 1, 'system', 0),
(4, 'Body & Thư Giãn Hoàng Gia', 'body-thu-gian', 'Liệu pháp massage đá nóng thảo dược kết hợp công nghệ tắm trắng phi thuyền Nano ánh sáng sinh học.', 'https://images.unsplash.com/photo-1540555700478-4be289fbecef?auto=format&fit=crop&w=800&q=80', 1, 'system', 0)
ON DUPLICATE KEY UPDATE name=VALUES(name), description=VALUES(description), thumbnail_url=VALUES(thumbnail_url);

-- 3. Beauty Services (Các dịch vụ Spa cụ thể)
INSERT INTO beauty_services (id, category_id, name, slug, short_description, description, base_price, duration_minutes, preparation_time_minutes, thumbnail_url, is_active, created_by, is_deleted)
VALUES
(1, 1, 'Chăm Sóc Da Mặt Aqua Peel Chuyên Sâu', 'cham-soc-da-mat-aqua-peel-chuyen-sau',
 'Làm sạch sâu lỗ chân lông với áp lực xoáy nước Aqua Peel, hút sạch bã nhờn và cấp ẩm Hyaluronic Acid.',
 'Liệu trình 60 phút bao gồm: Tẩy trang sinh học, Rửa mặt bọt mịn enzyme, Tẩy da chết sóng siêu âm, Hút dầu nhờn mụn cám bằng máy Aqua Peel chân không, Đắp mặt nạ dịu da phục hồi B5, Điện di lạnh tinh chất HA khóa ẩm và thoa kem chống nắng bảo vệ.',
 450000.00, 60, 15, 'https://images.unsplash.com/photo-1570172619644-dfd03ed5d881?auto=format&fit=crop&w=800&q=80', 1, 'system', 0),

(2, 2, 'Liệu Trình Điều Trị Mụn Chuẩn Y Khoa', 'dieu-tri-mun-chuan-y-khoa',
 'Phác đồ lấy nhân mụn vô khuẩn, chiếu ánh sáng sinh học Bio-Light và diệt khuẩn nang lông tầng sâu.',
 'Quy trình chuẩn y khoa 75 phút được thực hiện bởi kỹ thuật viên tay nghề cao: Thăm khám phân loại mụn, Xông hơi thảo mộc giãn nở lỗ chân lông, Lấy nhân mụn bằng tăm bông/kim y tế tiệt trùng 1 lần, Sát khuẩn tia điện tím Plasma lạnh, Đắp mặt nạ kháng viêm tràm trà và chiếu đèn ánh sáng sinh học Blue Light.',
 650000.00, 75, 15, 'https://images.unsplash.com/photo-1512290900672-1f4142f1f008?auto=format&fit=crop&w=800&q=80', 1, 'system', 0),

(3, 1, 'Điện Di Tinh Chất Vitamin C & Niacinamide Căng Bóng', 'dien-di-tinh-chat-vitamin-c-cang-bong',
 'Tăng sinh collagen, mờ thâm sạm và mang lại làn da trắng hồng sáng mịn nhờ công nghệ điện di ion Galvanic.',
 'Phương pháp đẩy sâu Vitamin C 20% tinh khiết vào lớp trung bì mà không gây tổn thương bề mặt da. Giúp ức chế sắc tố melanin, phục hồi da cháy nắng và chống oxy hóa mạnh mẽ.',
 550000.00, 45, 15, 'https://images.unsplash.com/photo-1560750588-73207b1ef5b8?auto=format&fit=crop&w=800&q=80', 1, 'system', 0),

(4, 3, 'Trẻ Hóa Nâng Cơ Siêu Âm Hifu Ultra V-Line', 'tre-hoa-nang-co-hifu-ultra-v-line',
 'Tạo hình đường viền hàm V-line sắc nét, xóa nếp nhăn đuôi mắt và kích thích collagen tầng sâu không xâm lấn.',
 'Công nghệ sóng siêu âm hội tụ vi điểm tác động chính xác vào lớp cân cơ nông SMAS ở độ sâu 3.0mm - 4.5mm. Nhiệt độ lý tưởng 60-70 độ C kích hoạt quá trình tự tái tạo mô sợi collagen và elastin, mang lại hiệu quả nâng cơ thon gọn ngay sau 1 buổi.',
 1850000.00, 90, 15, 'https://images.unsplash.com/photo-1522337360788-8b13dee7a37e?auto=format&fit=crop&w=800&q=80', 1, 'system', 0),

(5, 3, 'Cấy Tinh Chất Collagen Tươi Tái Sinh Làn Da', 'cay-tinh-chat-collagen-tuoi-tai-sinh-da',
 'Cung cấp trực tiếp các chuỗi peptide collagen tươi phân tử siêu nhỏ, phục hồi làn da khô ráp và lão hóa sớm.',
 'Liệu trình kết hợp công nghệ điện di áp suất vi điểm không kim và mặt nạ tơ tằm vàng 24K, giúp da ẩm mượt, căng mọng ngậm nước suốt 30 ngày.',
 1200000.00, 60, 15, 'https://images.unsplash.com/photo-1616394584738-fc6e612e71b9?auto=format&fit=crop&w=800&q=80', 1, 'system', 0),

(6, 2, 'Liệu Trình Trị Thâm Nám Laser Toning Bio-Cell', 'tri-tham-nam-laser-toning-bio-cell',
 'Bắn phá các hạt hắc sắc tố Melanin sâu dưới trung bì, xóa mờ vết nám mảng và tàn nhang hiệu quả an toàn.',
 'Sử dụng bước sóng Laser Nd:YAG 1064nm chọn lọc, phá vỡ hắc tố thành các hạt siêu nhỏ để đại thực bào tự đào thải tự nhiên qua hệ bài tiết, không làm bong tróc hay kích ứng da.',
 1500000.00, 60, 15, 'https://images.unsplash.com/photo-1487412720507-e7ab37603c6f?auto=format&fit=crop&w=800&q=80', 1, 'system', 0),

(7, 4, 'Massage Toàn Thân Đá Nóng & Thảo Dược Thụy Điển', 'massage-toan-than-da-nong-thao-duoc',
 'Xua tan căng thẳng mệt mỏi, đã thông kinh lạc và tăng cường tuần hoàn máu với đá bazan núi lửa tự nhiên.',
 'Sự kết hợp hoàn hảo giữa kỹ thuật miết vuốt thư giãn Thụy Điển và nhiệt lượng ấm áp từ đá núi lửa cùng tinh dầu oải hương nguyên chất, giúp ngủ sâu giấc và giải phóng ứ trệ cơ xương khớp.',
 480000.00, 60, 15, 'https://images.unsplash.com/photo-1519823551278-64ac92734fb1?auto=format&fit=crop&w=800&q=80', 1, 'system', 0),

(8, 4, 'Tắm Trắng Toàn Thân Công Nghệ Phi Thuyền Hoàng Gia', 'tam-trang-phi-thuyen-hoang-gia',
 'Bật 2-3 tone da tự nhiên sau liệu trình với tinh chất ngọc trai thiên nhiên và ánh sáng nhiệt quang hồng ngoại.',
 'Công nghệ phi thuyền hồng ngoại đẩy sâu tinh chất ủ trắng thảo mộc và vitamin vào tế bào sừng, loại bỏ lớp sừng cằn cỗi thâm sạm, mang lại làn da body mịn màng như lụa.',
 950000.00, 90, 15, 'https://images.unsplash.com/photo-1540555700478-4be289fbecef?auto=format&fit=crop&w=800&q=80', 1, 'system', 0)
ON DUPLICATE KEY UPDATE category_id=VALUES(category_id), name=VALUES(name), slug=VALUES(slug), short_description=VALUES(short_description), description=VALUES(description), base_price=VALUES(base_price), duration_minutes=VALUES(duration_minutes), preparation_time_minutes=VALUES(preparation_time_minutes), thumbnail_url=VALUES(thumbnail_url);

-- 4. Staff Users & Staff Records (Chuyên viên & Bác sĩ Da liễu)
UPDATE users SET full_name = 'BS. Nguyễn Thị Mai Anh' WHERE id = 3;
UPDATE users SET full_name = 'Test Client 01' WHERE id = 5;

INSERT INTO users (id, email, phone, password_hash, full_name, username, is_deleted)
VALUES
(6, 'dr.thao@beautyshop.vn', '0933112233', '$2a$10$vI8aWBnW3fID.ZQ4/zo1G.q1qYe4vK14h1K9w1aE/5L8F.Y1bXg.W', 'BS. CKII Trần Thu Thảo', 'drthao', 0),
(7, 'ktv.lan@beautyshop.vn', '0933445566', '$2a$10$vI8aWBnW3fID.ZQ4/zo1G.q1qYe4vK14h1K9w1aE/5L8F.Y1bXg.W', 'KTV. Lê Hoàng Lan', 'ktvlan', 0)
ON DUPLICATE KEY UPDATE full_name=VALUES(full_name);

INSERT INTO user_roles (user_id, role_id)
VALUES (6, 3), (7, 3)
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);

INSERT INTO staffs (id, user_id, specialty, bio, rating, total_reviews, is_active, created_by, is_deleted)
VALUES
(1, 3, 'Chuyên viên Chăm sóc da & Trị liệu nâng cao', 'Hơn 6 năm kinh nghiệm thẩm mỹ da liễu và chăm sóc da chuyên sâu tại hệ thống Beauty Shop Clinic.', 4.9, 128, 1, 'system', 0),
(2, 6, 'Bác sĩ Chuyên khoa II Da liễu & Laser Thẩm mỹ', 'Tốt nghiệp ĐH Y Dược, 8 năm kinh nghiệm chuyên sâu về laser toning, trẻ hóa HIFU và điều trị sắc tố da.', 5.0, 215, 1, 'system', 0),
(3, 7, 'Kỹ thuật viên Trị liệu Body & Spa Thư giãn', 'Chứng chỉ spa quốc tế CIDESCO, chuyên gia kỹ thuật massage Thụy Điển và phục hồi sức khỏe làn da toàn thân.', 4.8, 96, 1, 'system', 0)
ON DUPLICATE KEY UPDATE specialty=VALUES(specialty), bio=VALUES(bio), rating=VALUES(rating);

-- 5. Staff Service Skills (Kỹ năng được chứng nhận cho từng dịch vụ)
INSERT INTO staff_service_skills (id, staff_id, service_id, is_certified, created_by, is_deleted)
VALUES
-- Staff 1 (BS. Mai Anh): Phụ trách dịch vụ 1, 2, 3, 5, 7
(1, 1, 1, 1, 'system', 0),
(2, 1, 2, 1, 'system', 0),
(3, 1, 3, 1, 'system', 0),
(4, 1, 5, 1, 'system', 0),
(5, 1, 7, 1, 'system', 0),

-- Staff 2 (BS. Trần Thu Thảo): Phụ trách dịch vụ 2, 4, 5, 6
(6, 2, 2, 1, 'system', 0),
(7, 2, 4, 1, 'system', 0),
(8, 2, 5, 1, 'system', 0),
(9, 2, 6, 1, 'system', 0),

-- Staff 3 (KTV. Lê Hoàng Lan): Phụ trách dịch vụ 1, 3, 7, 8
(10, 3, 1, 1, 'system', 0),
(11, 3, 3, 1, 'system', 0),
(12, 3, 7, 1, 'system', 0),
(13, 3, 8, 1, 'system', 0)
ON DUPLICATE KEY UPDATE is_certified=VALUES(is_certified);

-- 6. Service Packages (Gói combo liệu trình Spa)
INSERT INTO service_packages (id, name, description, price, validity_days, thumbnail_url, is_active, created_by, is_deleted)
VALUES
(1, 'Combo 5 Buổi Chăm Sóc Da Toàn Diện Aqua Peel',
 'Gói 5 buổi chăm sóc da chuyên sâu Aqua Peel giúp làm sạch sâu, hút sạch bã nhờn mụn cám và cấp ẩm tầng sâu đều đặn mỗi 2 tuần. Tiết kiệm 20% so với đặt từng buổi lẻ.',
 1800000.00, 180, 'https://images.unsplash.com/photo-1570172619644-dfd03ed5d881?auto=format&fit=crop&w=800&q=80', 1, 'system', 0),

(2, 'Liệu Trình Điều Trị Mụn Tận Gốc Y Khoa 5 Buổi',
 'Phác đồ 5 buổi điều trị mụn y khoa kết hợp chiếu đèn sinh học Bio-Light và diệt khuẩn nang lông chuyên sâu, cam kết giảm mụn viêm và phục hồi da rõ rệt.',
 2600000.00, 120, 'https://images.unsplash.com/photo-1512290900672-1f4142f1f008?auto=format&fit=crop&w=800&q=80', 1, 'system', 0),

(3, 'Gói Trẻ Hóa & Căng Bóng Da Cao Cấp (Hifu + Collagen)',
 'Combo cao cấp gồm 1 buổi nâng cơ trẻ hóa HIFU Ultra V-line toàn mặt và 2 buổi cấy tinh chất Collagen tươi, mang lại nét mặt thon gọn và căng tràn sức sống.',
 3900000.00, 365, 'https://images.unsplash.com/photo-1522337360788-8b13dee7a37e?auto=format&fit=crop&w=800&q=80', 1, 'system', 0),

(4, 'Gói Phục Hồi & Thư Giãn Hoàng Gia 3 Buổi',
 'Gói trị liệu thư giãn chuyên sâu gồm 2 buổi Massage toàn thân đá nóng thảo dược Thụy Điển và 1 buổi chăm sóc da mặt Aqua Peel thư thái.',
 1250000.00, 90, 'https://images.unsplash.com/photo-1540555700478-4be289fbecef?auto=format&fit=crop&w=800&q=80', 1, 'system', 0)
ON DUPLICATE KEY UPDATE name=VALUES(name), description=VALUES(description), price=VALUES(price), validity_days=VALUES(validity_days), thumbnail_url=VALUES(thumbnail_url);

-- 7. Service Package Items (Số lượng buổi cho từng dịch vụ trong gói)
INSERT INTO service_package_items (id, package_id, service_id, quantity, created_by, is_deleted)
VALUES
-- Gói 1: 5 buổi Dịch vụ 1 (Aqua Peel)
(1, 1, 1, 5, 'system', 0),

-- Gói 2: 5 buổi Dịch vụ 2 (Trị mụn y khoa)
(2, 2, 2, 5, 'system', 0),

-- Gói 3: 1 buổi Dịch vụ 4 (HIFU) + 2 buổi Dịch vụ 5 (Collagen tươi)
(3, 3, 4, 1, 'system', 0),
(4, 3, 5, 2, 'system', 0),

-- Gói 4: 2 buổi Dịch vụ 7 (Massage đá nóng) + 1 buổi Dịch vụ 1 (Aqua Peel)
(5, 4, 7, 2, 'system', 0),
(6, 4, 1, 1, 'system', 0)
ON DUPLICATE KEY UPDATE quantity=VALUES(quantity);
