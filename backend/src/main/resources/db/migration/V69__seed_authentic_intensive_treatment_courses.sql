-- =========================================================================
-- V69: SEED AUTHENTIC CLINICAL INTENSIVE TREATMENT COURSES & PROTOCOLS
-- Tham khao du lieu lam sang y khoa thuc te tu Hasaki Clinic, Rohto Aohal & Seoul Spa
-- =========================================================================

-- 1. Them 4 Dich vu Phac do Lieu trinh Chuyen sau (TREATMENT_COURSE)
INSERT INTO beauty_services (id, category_id, name, slug, short_description, description, base_price, duration_minutes, preparation_time_minutes, thumbnail_url, is_active, service_type, created_by, is_deleted)
VALUES
(9, 2, 'Phác Đồ Trị Mụn Chuẩn Y Khoa & Kháng Viêm Bio-Light (5 Buổi)',
 'phac-do-tri-mun-y-khoa-5-buoi',
 'Phác đồ 5 buổi chuẩn y khoa Hasaki Clinic điều trị mụn viêm, mụn ẩn, gom cồi và phục hồi da sau mụn.',
 'Phác đồ chuyên sâu 5 giai đoạn do Bác sĩ Da liễu thiết kế: Lấy nhân mụn vô khuẩn bằng tăm bông y tế, sát khuẩn điện tím Plasma lạnh, đắp mặt nạ tràm trà giảm sưng đỏ, chiếu ánh sáng sinh học Bio-Light và điện di phục hồi tinh chất B5.',
 2850000.00, 75, 15,
 'https://images.unsplash.com/photo-1512290900672-1f4142f1f008?auto=format&fit=crop&w=800&q=80',
 1, 'TREATMENT_COURSE', 'system', 0),

(10, 2, 'Phác Đồ Xóa Nám Mảng & Trẻ Hóa Sắc Tố Laser Toning (6 Buổi)',
 'phac-do-xoa-nam-laser-toning-6-buoi',
 'Phác đồ 6 buổi ứng dụng công nghệ Laser Nd:YAG 1064nm phá vỡ hắc sắc tố Melanin kết hợp điện di tinh chất căng bóng.',
 'Liệu trình y khoa chuyên sâu kết hợp Laser Toning bước sóng 1064nm chọn lọc với điện di Vitamin C 20% & Niacinamide tinh khiết, ức chế enzyme Tyrosinase, làm mờ nám mảng, tàn nhang và phục hồi hàng rào màng đáy da.',
 7200000.00, 60, 15,
 'https://images.unsplash.com/photo-1487412720507-e7ab37603c6f?auto=format&fit=crop&w=800&q=80',
 1, 'TREATMENT_COURSE', 'system', 0),

(11, 3, 'Phác Đồ Nâng Cơ Trẻ Hóa Tầng Sâu HIFU Ultra V-Line (3 Buổi)',
 'phac-do-nang-co-hifu-v-line-3-buoi',
 'Phác đồ 3 buổi kết hợp sóng siêu âm hội tụ vi điểm SMAS HIFU và cấy peptide collagen tươi phục hồi săn chắc.',
 'Liệu pháp trẻ hóa không phẫu thuật hàng đầu tại Seoul Spa & Hasaki Clinic. Tác động nhiệt 60-70 độ C vào lớp cân cơ nông SMAS độ sâu 4.5mm, thon gọn đường viền hàm, nâng cơ chảy xệ và duy trì độ ẩm căng bóng bằng collagen tươi.',
 4200000.00, 90, 15,
 'https://images.unsplash.com/photo-1522337360788-8b13dee7a37e?auto=format&fit=crop&w=800&q=80',
 1, 'TREATMENT_COURSE', 'system', 0),

(12, 1, 'Phác Đồ Cấp Ẩm Đa Tầng & Phục Hồi Hàng Rào Bảo Vệ Da (4 Buổi)',
 'phac-do-cap-am-phuc-hoi-da-4-buoi',
 'Phác đồ 4 buổi thanh lọc bã nhờn xoáy nước Aqua Peel, hút mụn cám và điện di tinh chất phục hồi màng lipid.',
 'Quy trình chăm sóc chuyên sâu chuẩn thẩm mỹ viện: Hút sạch tế bào sừng cằn cỗi và bã nhờn bít tắc bằng áp lực xoáy nước Aqua Peel, cân bằng pH, điện di tinh chất Hyaluronic Acid & Ceramide giúp da ngậm nước căng mướt và khỏe mạnh.',
 1700000.00, 60, 15,
 'https://images.unsplash.com/photo-1570172619644-dfd03ed5d881?auto=format&fit=crop&w=800&q=80',
 1, 'TREATMENT_COURSE', 'system', 0)
ON DUPLICATE KEY UPDATE
 category_id = VALUES(category_id),
 name = VALUES(name),
 slug = VALUES(slug),
 short_description = VALUES(short_description),
 description = VALUES(description),
 base_price = VALUES(base_price),
 duration_minutes = VALUES(duration_minutes),
 thumbnail_url = VALUES(thumbnail_url),
 is_active = VALUES(is_active),
 service_type = VALUES(service_type);

-- 2. Goi dich vu thanh toan noi bo quan ly quyen loi phac do (treatment_managed = 1, is_active = 0)
INSERT INTO service_packages (id, name, description, price, validity_days, thumbnail_url, is_active, treatment_managed, created_by, is_deleted)
VALUES
(5, 'Phác Đồ Trị Mụn Chuẩn Y Khoa & Kháng Viêm Bio-Light (5 Buổi)',
 'Phác đồ 5 buổi chuyên sâu chuẩn y khoa được thiết kế dựa trên quy chuẩn điều trị da liễu tại Hasaki Clinic. Kết hợp lấy nhân mụn vô khuẩn, sát khuẩn tia Plasma lạnh, hút sạch bã nhờn Aqua Peel, chiếu ánh sáng sinh học Bio-Light diệt khuẩn P.acnes và điện di tinh chất phục hồi màng lipid B5.',
 2850000.00, 120, 'https://images.unsplash.com/photo-1512290900672-1f4142f1f008?auto=format&fit=crop&w=800&q=80', 0, 1, 'system', 0),

(6, 'Phác Đồ Xóa Nám Mảng & Trẻ Hóa Sắc Tố Laser Toning (6 Buổi)',
 'Phác đồ 6 buổi ứng dụng công nghệ Laser Nd:YAG 1064nm chuẩn y khoa từ Rohto Aohal Clinic. Bắn phá chọn lọc hắc sắc tố Melanin tầng trung bì, kết hợp điện di tinh chất Vitamin C 20% & Niacinamide tinh khiết giúp xóa mờ nám mảng, tàn nhang và dưỡng sáng đều màu da.',
 7200000.00, 180, 'https://images.unsplash.com/photo-1487412720507-e7ab37603c6f?auto=format&fit=crop&w=800&q=80', 0, 1, 'system', 0),

(7, 'Phác Đồ Nâng Cơ Trẻ Hóa Tầng Sâu HIFU Ultra V-Line (3 Buổi)',
 'Liệu pháp trẻ hóa và nâng cơ không phẫu thuật chuẩn Seoul Spa & Hasaki Clinic. Ứng dụng sóng siêu âm hội tụ vi điểm tác động nhiệt 60-70 độ C vào lớp cân cơ nông SMAS độ sâu 4.5mm, thon gọn đường viền hàm, nâng mí sụp và kích sinh mạng lưới collagen săn chắc.',
 4200000.00, 180, 'https://images.unsplash.com/photo-1522337360788-8b13dee7a37e?auto=format&fit=crop&w=800&q=80', 0, 1, 'system', 0),

(8, 'Phác Đồ Cấp Ẩm Đa Tầng & Phục Hồi Hàng Rào Bảo Vệ Da (4 Buổi)',
 'Phác đồ 4 buổi phục hồi chuyên sâu dành cho làn da thiếu ẩm, khô sần, nhạy cảm sau điều trị. Ứng dụng áp lực xoáy nước Aqua Peel làm sạch bã nhờn kết hợp điện di tinh chất B5 Panthenol, Ceramide và cấy Collagen tươi ngậm nước đa tầng.',
 1700000.00, 90, 'https://images.unsplash.com/photo-1570172619644-dfd03ed5d881?auto=format&fit=crop&w=800&q=80', 0, 1, 'system', 0)
ON DUPLICATE KEY UPDATE
 name = VALUES(name),
 description = VALUES(description),
 price = VALUES(price),
 validity_days = VALUES(validity_days),
 thumbnail_url = VALUES(thumbnail_url),
 treatment_managed = VALUES(treatment_managed);

-- 3. Dinh muc so luong buoi cho tung dich vu don le trong goi phac do
INSERT INTO service_package_items (id, package_id, service_id, quantity, created_by, is_deleted)
VALUES
(7, 5, 2, 2, 'system', 0),
(8, 5, 1, 1, 'system', 0),
(9, 5, 3, 1, 'system', 0),
(10, 5, 5, 1, 'system', 0),

(11, 6, 6, 3, 'system', 0),
(12, 6, 3, 2, 'system', 0),
(13, 6, 5, 1, 'system', 0),

(14, 7, 4, 1, 'system', 0),
(15, 7, 5, 1, 'system', 0),
(16, 7, 3, 1, 'system', 0),

(17, 8, 1, 2, 'system', 0),
(18, 8, 3, 1, 'system', 0),
(19, 8, 5, 1, 'system', 0)
ON DUPLICATE KEY UPDATE quantity = VALUES(quantity);

-- 4. Bang mau phac do dieu tri (Treatment Templates)
INSERT INTO treatment_templates (id, service_id, published_version_id, draft_version_id, archived, created_at)
VALUES
(1, 9, NULL, NULL, 0, CURRENT_TIMESTAMP(6)),
(2, 10, NULL, NULL, 0, CURRENT_TIMESTAMP(6)),
(3, 11, NULL, NULL, 0, CURRENT_TIMESTAMP(6)),
(4, 12, NULL, NULL, 0, CURRENT_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE service_id = VALUES(service_id);

-- 5. Bang phien ban phac do xuat ban kem cau truc plan_json chi tiet
INSERT INTO treatment_template_versions (id, template_id, version_number, package_id, plan_json, status, created_at, published_at)
VALUES
(1, 1, 1, 5,
 '{"name":"Phác Đồ Trị Mụn Chuẩn Y Khoa & Kháng Viêm Bio-Light (5 Buổi)","description":"Phác đồ 5 buổi chuyên sâu chuẩn y khoa được thiết kế dựa trên quy chuẩn điều trị da liễu tại Hasaki Clinic. Kết hợp lấy nhân mụn vô khuẩn, sát khuẩn tia Plasma lạnh, hút sạch bã nhờn Aqua Peel, chiếu ánh sáng sinh học Bio-Light diệt khuẩn P.acnes và điện di tinh chất phục hồi màng lipid B5.","serviceId":9,"price":2850000.00,"validityDays":120,"terms":"1. Phác đồ được Bác sĩ Da liễu chỉ định và theo dõi xuyên suốt 5 buổi điều trị.\\n2. Khách hàng tuân thủ khoảng cách tối thiểu giữa các buổi (từ 7 đến 14 ngày) để làn da có chu kỳ tái tạo và gom cồi mụn tối ưu.\\n3. Mỗi buổi sử dụng bộ dụng cụ tăm bông và kim y tế tiệt trùng riêng biệt, vô khuẩn 100% chuẩn y khoa.\\n4. Sau buổi lấy mụn, cần giữ da sạch, tránh nước nóng và trang điểm trong 24 giờ đầu, sử dụng kem chống nắng phổ rộng theo hướng dẫn.\\n5. Toàn bộ quyền lợi buổi điều trị chưa sử dụng được bảo lưu và đối soát điện tử trong thời hạn 120 ngày kể từ ngày thanh toán.","sessions":[{"sequence":1,"title":"Buổi 1: Thăm khám da Visia, lấy nhân mụn vô khuẩn & chiếu ánh sáng Bio-Light","minGapDays":0,"maxGapDays":null,"required":true,"items":[{"serviceId":2,"serviceName":"Liệu Trình Điều Trị Mụn Chuẩn Y Khoa","required":true}]},{"sequence":2,"title":"Buổi 2: Làm sạch bã nhờn xoáy nước Aqua Peel & làm dịu kháng viêm tràm trà","minGapDays":7,"maxGapDays":14,"required":true,"items":[{"serviceId":1,"serviceName":"Chăm Sóc Da Mặt Aqua Peel Chuyên Sâu","required":true}]},{"sequence":3,"title":"Buổi 3: Lấy nhân mụn viêm tầng sâu đợt 2 & sát khuẩn tia Plasma lạnh","minGapDays":7,"maxGapDays":14,"required":true,"items":[{"serviceId":2,"serviceName":"Liệu Trình Điều Trị Mụn Chuẩn Y Khoa","required":true}]},{"sequence":4,"title":"Buổi 4: Điện di tinh chất Vitamin C & Niacinamide mờ thâm đỏ sau mụn","minGapDays":10,"maxGapDays":21,"required":true,"items":[{"serviceId":3,"serviceName":"Điện Di Tinh Chất Vitamin C & Niacinamide Căng Bóng","required":true}]},{"sequence":5,"title":"Buổi 5: Cấy tinh chất Collagen tươi tái sinh và phục hồi màng bảo vệ da","minGapDays":14,"maxGapDays":28,"required":true,"items":[{"serviceId":5,"serviceName":"Cấy Tinh Chất Collagen Tươi Tái Sinh Làn Da","required":true}]}]}',
 'PUBLISHED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),

(2, 2, 1, 6,
 '{"name":"Phác Đồ Xóa Nám Mảng & Trẻ Hóa Sắc Tố Laser Toning (6 Buổi)","description":"Phác đồ 6 buổi ứng dụng công nghệ Laser Nd:YAG 1064nm chuẩn y khoa từ Rohto Aohal Clinic. Bắn phá chọn lọc hắc sắc tố Melanin tầng trung bì, kết hợp điện di tinh chất Vitamin C 20% & Niacinamide tinh khiết giúp xóa mờ nám mảng, tàn nhang và dưỡng sáng đều màu da.","serviceId":10,"price":7200000.00,"validityDays":180,"terms":"1. Bác sĩ Da liễu trực tiếp thăm khám và điều chỉnh thông số năng lượng Laser phù hợp với từng mức độ nám.\\n2. Khoảng cách tối thiểu giữa các buổi bắn Laser Toning là 14 ngày để đại thực bào hoàn tất quá trình đào thải hạt sắc tố tự nhiên.\\n3. Khách hàng bắt buộc thoa kem chống nắng SPF50+ PA++++ hàng ngày và tái bôi sau mỗi 2 - 3 giờ khi tiếp xúc ánh nắng.\\n4. Chống chỉ định phụ nữ có thai, người có tiền sử sẹo lồi hoặc đang dùng thuốc nhạy cảm ánh sáng.\\n5. Quyền lợi các buổi điều trị được kích hoạt vé điện tử và có giá trị trong 180 ngày.","sessions":[{"sequence":1,"title":"Buổi 1: Soi da chẩn đoán sắc tố trung bì & Bắn Laser Toning phiên 1","minGapDays":0,"maxGapDays":null,"required":true,"items":[{"serviceId":6,"serviceName":"Liệu Trình Trị Thâm Nám Laser Toning Bio-Cell","required":true}]},{"sequence":2,"title":"Buổi 2: Điện di tinh chất Vitamin C & Niacinamide ức chế Tyrosinase","minGapDays":10,"maxGapDays":21,"required":true,"items":[{"serviceId":3,"serviceName":"Điện Di Tinh Chất Vitamin C & Niacinamide Căng Bóng","required":true}]},{"sequence":3,"title":"Buổi 3: Bắn Laser Toning phiên 2 phá vỡ melanin tầng sâu","minGapDays":14,"maxGapDays":28,"required":true,"items":[{"serviceId":6,"serviceName":"Liệu Trình Trị Thâm Nám Laser Toning Bio-Cell","required":true}]},{"sequence":4,"title":"Buổi 4: Cấy tinh chất Collagen tươi củng cố cấu trúc màng đáy","minGapDays":14,"maxGapDays":28,"required":true,"items":[{"serviceId":5,"serviceName":"Cấy Tinh Chất Collagen Tươi Tái Sinh Làn Da","required":true}]},{"sequence":5,"title":"Buổi 5: Bắn Laser Toning phiên 3 tinh chỉnh sắc tố vi điểm","minGapDays":14,"maxGapDays":30,"required":true,"items":[{"serviceId":6,"serviceName":"Liệu Trình Trị Thâm Nám Laser Toning Bio-Cell","required":true}]},{"sequence":6,"title":"Buổi 6: Điện di phục hồi chuyên sâu, khóa ẩm tầng sâu & đánh giá kết quả soi da","minGapDays":14,"maxGapDays":30,"required":true,"items":[{"serviceId":3,"serviceName":"Điện Di Tinh Chất Vitamin C & Niacinamide Căng Bóng","required":true}]}]}',
 'PUBLISHED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),

(3, 3, 1, 7,
 '{"name":"Phác Đồ Nâng Cơ Trẻ Hóa Tầng Sâu HIFU Ultra V-Line (3 Buổi)","description":"Liệu pháp trẻ hóa và nâng cơ không phẫu thuật chuẩn Seoul Spa & Hasaki Clinic. Ứng dụng sóng siêu âm hội tụ vi điểm tác động nhiệt 60-70 độ C vào lớp cân cơ nông SMAS độ sâu 4.5mm, thon gọn đường viền hàm, nâng mí sụp và kích sinh mạng lưới collagen săn chắc.","serviceId":11,"price":4200000.00,"validityDays":180,"terms":"1. Sóng siêu âm hội tụ HIFU tác động chuẩn xác vào lớp cân cơ SMAS mà không gây tổn thương bề mặt da, không cần nghỉ dưỡng.\\n2. Tránh rửa mặt bằng nước nóng hoặc xông hơi trong vòng 7 ngày đầu sau buổi đi máy HIFU.\\n3. Uống đủ 2 lít nước mỗi ngày để tạo môi trường lý tưởng cho tế bào tự sản sinh collagen và elastin mới.\\n4. Khoảng cách tối thiểu giữa buổi nâng cơ và các buổi dưỡng là 21 ngày.\\n5. Toàn bộ tiến trình điều trị được lưu trữ điện tử và bảo lưu trong 180 ngày.","sessions":[{"sequence":1,"title":"Buổi 1: Thăm khám cơ mặt & Nâng cơ siêu âm hội tụ HIFU SMAS vi điểm toàn mặt","minGapDays":0,"maxGapDays":null,"required":true,"items":[{"serviceId":4,"serviceName":"Trẻ Hóa Nâng Cơ Siêu Âm Hifu Ultra V-Line","required":true}]},{"sequence":2,"title":"Buổi 2: Cấy tinh chất Collagen tươi & Hyaluronic Acid dưỡng ẩm mô liên kết","minGapDays":21,"maxGapDays":45,"required":true,"items":[{"serviceId":5,"serviceName":"Cấy Tinh Chất Collagen Tươi Tái Sinh Làn Da","required":true}]},{"sequence":3,"title":"Buổi 3: Điện di tinh chất Vitamin C & Niacinamide dưỡng sáng và tăng độ đàn hồi","minGapDays":21,"maxGapDays":60,"required":true,"items":[{"serviceId":3,"serviceName":"Điện Di Tinh Chất Vitamin C & Niacinamide Căng Bóng","required":true}]}]}',
 'PUBLISHED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),

(4, 4, 1, 8,
 '{"name":"Phác Đồ Cấp Ẩm Đa Tầng & Phục Hồi Hàng Rào Bảo Vệ Da (4 Buổi)","description":"Phác đồ 4 buổi phục hồi chuyên sâu dành cho làn da thiếu ẩm, khô sần, nhạy cảm sau điều trị. Ứng dụng áp lực xoáy nước Aqua Peel làm sạch bã nhờn kết hợp điện di tinh chất B5 Panthenol, Ceramide và cấy Collagen tươi ngậm nước đa tầng.","serviceId":12,"price":1700000.00,"validityDays":90,"terms":"1. Phù hợp cho mọi loại da, đặc biệt là da thiếu ẩm, xỉn màu và da sau đợt điều trị mụn.\\n2. Khoảng cách khuyến nghị là 7 - 14 ngày/buổi để duy trì độ ẩm tự nhiên của hàng rào bảo vệ da.\\n3. Sau buổi chăm sóc, da ngậm nước căng mọng tức thì, không gây bong tróc hay đỏ rát.\\n4. Thời hạn sử dụng gói là 90 ngày kể từ ngày thanh toán.","sessions":[{"sequence":1,"title":"Buổi 1: Aqua Peel thanh lọc lỗ chân lông & Điện di tinh chất HA tầng sâu","minGapDays":0,"maxGapDays":null,"required":true,"items":[{"serviceId":1,"serviceName":"Chăm Sóc Da Mặt Aqua Peel Chuyên Sâu","required":true}]},{"sequence":2,"title":"Buổi 2: Điện di tinh chất Vitamin C & Niacinamide phục hồi sắc tố và cân bằng bã nhờn","minGapDays":7,"maxGapDays":14,"required":true,"items":[{"serviceId":3,"serviceName":"Điện Di Tinh Chất Vitamin C & Niacinamide Căng Bóng","required":true}]},{"sequence":3,"title":"Buổi 3: Aqua Peel làm sạch bã nhờn chu kỳ 2 & Mặt nạ làm dịu B5","minGapDays":10,"maxGapDays":21,"required":true,"items":[{"serviceId":1,"serviceName":"Chăm Sóc Da Mặt Aqua Peel Chuyên Sâu","required":true}]},{"sequence":4,"title":"Buổi 4: Cấy tinh chất Collagen tươi khóa ẩm căng bóng tầng sâu","minGapDays":14,"maxGapDays":28,"required":true,"items":[{"serviceId":5,"serviceName":"Cấy Tinh Chất Collagen Tươi Tái Sinh Làn Da","required":true}]}]}',
 'PUBLISHED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE
 plan_json = VALUES(plan_json),
 status = VALUES(status),
 package_id = VALUES(package_id);

-- 6. Cap nhat ban ghi mau tro den phien ban xuat ban chinh thuc
UPDATE treatment_templates SET published_version_id = 1 WHERE id = 1;
UPDATE treatment_templates SET published_version_id = 2 WHERE id = 2;
UPDATE treatment_templates SET published_version_id = 3 WHERE id = 3;
UPDATE treatment_templates SET published_version_id = 4 WHERE id = 4;

-- 7. Cap chung chi ky nang phuc vu toan bo cac dich vu cho doi ngu chuyen vien Spa
INSERT INTO staff_service_skills (staff_id, service_id, is_certified, created_by, is_deleted)
SELECT s.id, b.id, 1, 'system', 0
FROM staffs s
CROSS JOIN beauty_services b
WHERE s.is_deleted = 0 AND s.is_active = 1
  AND b.is_deleted = 0 AND b.is_active = 1
  AND NOT EXISTS (
      SELECT 1 FROM staff_service_skills sss
      WHERE sss.staff_id = s.id AND sss.service_id = b.id
  );
