-- Migration V46: Chuẩn hóa hệ thống về 3 Role duy nhất (ROLE_ADMIN, ROLE_STAFF, ROLE_USER)

-- 1. Đảm bảo role ROLE_USER tồn tại
INSERT INTO roles (name, description, is_deleted, created_at, updated_at)
SELECT 'ROLE_USER', 'Người dùng / Khách hàng hệ thống', FALSE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_USER');

-- 2. Đảm bảo role ROLE_STAFF tồn tại
INSERT INTO roles (name, description, is_deleted, created_at, updated_at)
SELECT 'ROLE_STAFF', 'Nhân viên vận hành cửa hàng & Spa', FALSE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_STAFF');

-- 3. Di chuyển người dùng có các role granular sang ROLE_STAFF
INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT ur.user_id, r_staff.id
FROM user_roles ur
JOIN roles r ON ur.role_id = r.id
JOIN roles r_staff ON r_staff.name = 'ROLE_STAFF'
WHERE r.name IN (
    'ROLE_ORDER_STAFF',
    'ROLE_INVENTORY_STAFF',
    'ROLE_CATALOG_STAFF',
    'ROLE_CS_STAFF',
    'ROLE_SPA_RECEPTION',
    'ROLE_SPA_THERAPIST'
);

-- 4. Di chuyển người dùng có ROLE_CUSTOMER sang ROLE_USER
INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT ur.user_id, r_user.id
FROM user_roles ur
JOIN roles r ON ur.role_id = r.id
JOIN roles r_user ON r_user.name = 'ROLE_USER'
WHERE r.name = 'ROLE_CUSTOMER';

-- 5. Xóa liên kết user_roles với các role cũ
DELETE ur FROM user_roles ur
JOIN roles r ON ur.role_id = r.id
WHERE r.name IN (
    'ROLE_ORDER_STAFF',
    'ROLE_INVENTORY_STAFF',
    'ROLE_CATALOG_STAFF',
    'ROLE_CS_STAFF',
    'ROLE_SPA_RECEPTION',
    'ROLE_SPA_THERAPIST',
    'ROLE_CUSTOMER'
);

-- 6. Xóa các role không còn sử dụng khỏi bảng roles
DELETE FROM roles
WHERE name IN (
    'ROLE_ORDER_STAFF',
    'ROLE_INVENTORY_STAFF',
    'ROLE_CATALOG_STAFF',
    'ROLE_CS_STAFF',
    'ROLE_SPA_RECEPTION',
    'ROLE_SPA_THERAPIST',
    'ROLE_CUSTOMER'
);

-- 7. Cập nhật email của tài khoản admin (chỉ cập nhật nếu chưa có tài khoản nào khác đang dùng email này)
UPDATE users
SET email = 'namnt4560@gmail.com'
WHERE username = 'admin'
  AND NOT EXISTS (
      SELECT 1 FROM (SELECT id FROM users WHERE email = 'namnt4560@gmail.com' AND username != 'admin') AS existing_user
  );

-- 8. Đảm bảo tài khoản mang email namnt4560@gmail.com và username admin đều có quyền ROLE_ADMIN
INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.name = 'ROLE_ADMIN'
WHERE u.email = 'namnt4560@gmail.com' OR u.username = 'admin';

