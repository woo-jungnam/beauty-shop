-- Migration V72: Đảm bảo tương thích ngược vai trò khách hàng (ROLE_USER và ROLE_CUSTOMER)

-- 1. Đảm bảo vai trò ROLE_USER tồn tại
INSERT INTO roles (name, description, is_deleted, created_at, updated_at)
SELECT 'ROLE_USER', 'Người dùng / Khách hàng hệ thống', FALSE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_USER');

-- 2. Đảm bảo vai trò ROLE_CUSTOMER tồn tại để tương thích ngược với các thành phần cũ
INSERT INTO roles (name, description, is_deleted, created_at, updated_at)
SELECT 'ROLE_CUSTOMER', 'Khách hàng hệ thống (tương thích ngược)', FALSE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_CUSTOMER');

