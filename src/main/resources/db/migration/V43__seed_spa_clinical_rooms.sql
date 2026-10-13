-- =========================================================================
-- V43: SEED DETAILED SPA TREATMENT ROOMS, SUITES & CLINICAL FACILITIES
-- =========================================================================

-- Update existing generic branch placeholder facilities to concrete clinical rooms
UPDATE facilities
SET name = 'Phòng 101 - Khám & Soi Da Visia',
    description = 'Tầng 1 - Khu vực thăm khám lâm sàng, máy soi da đa quang phổ 3D Visia',
    resource_type = 'CONSULTATION',
    capacity = 2,
    is_active = 1
WHERE id = 1;

UPDATE facilities
SET name = 'Phòng 102 - Aqua Peel & Làm Sạch Y Khoa',
    description = 'Tầng 1 - Hệ thống hút chân không bã nhờn Aqua Peel và tẩy tế bào chết enzyme',
    resource_type = 'AQUA_PEEL',
    capacity = 3,
    is_active = 1
WHERE id = 2;

-- Insert specialized clinical treatment rooms & suites
INSERT INTO facilities (id, name, description, resource_type, capacity, is_active, created_by, is_deleted)
VALUES
(3, 'Phòng 201 - Trị Liệu Mụn Y Khoa & Bio-Light', 'Tầng 2 - Phòng vô trùng lấy nhân mụn, sát khuẩn Plasma lạnh và chiếu đèn sinh học Blue-Light', 'ACNE_CLINIC', 3, 1, 'system', 0),
(4, 'Phòng 202 - Điện Di Tinh Chất & Căng Bóng Da', 'Tầng 2 - Máy điện di ion Galvanic thẩm thấu Vitamin C, Niacinamide và tinh chất HA', 'GALVANIC', 3, 1, 'system', 0),
(5, 'Phòng 301 - Laser Toning & Trẻ Hóa HIFU SMAS', 'Tầng 3 - Công nghệ laser Nd:YAG 1064nm và máy nâng cơ siêu âm hội tụ HIFU Ultra V-line', 'HIGH_TECH', 2, 1, 'system', 0),
(6, 'Phòng VIP 302 - Chăm Sóc Da Hoàng Gia Suite', 'Tầng 3 - Phòng đơn VIP khép kín cao cấp, cấy tinh chất Collagen tươi và mặt nạ tơ tằm vàng 24K', 'VIP_SUITE', 1, 1, 'system', 0),
(7, 'Phòng 401 - Trị Liệu Body & Massage Thảo Dược', 'Tầng 4 - Không gian tĩnh lặng, giường sưởi thảo mộc và đá bazan núi lửa Thụy Điển', 'BODY_THERAPY', 4, 1, 'system', 0),
(8, 'Phòng 402 - Tắm Trắng Phi Thuyền Nano Hồng Ngoại', 'Tầng 4 - Hệ thống phi thuyền nhiệt quang collagen dưỡng trắng toàn thân', 'WHITENING_ROOM', 2, 1, 'system', 0)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    resource_type = VALUES(resource_type),
    capacity = VALUES(capacity),
    is_active = VALUES(is_active);
