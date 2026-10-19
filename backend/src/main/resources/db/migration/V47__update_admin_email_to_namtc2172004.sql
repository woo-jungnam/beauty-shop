-- =========================================================================
-- V47: UPDATE ADMIN EMAIL TO namtc2172004@gmail.com
-- =========================================================================

-- 1. Cập nhật email của tài khoản admin
UPDATE users
SET email = 'namtc2172004@gmail.com'
WHERE username = 'admin' OR id = 1;

-- 2. Đảm bảo tài khoản mang email namtc2172004@gmail.com và username admin có quyền ROLE_ADMIN
INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.name = 'ROLE_ADMIN'
WHERE u.email = 'namtc2172004@gmail.com' OR u.username = 'admin';
