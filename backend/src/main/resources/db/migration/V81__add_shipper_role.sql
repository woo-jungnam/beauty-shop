INSERT INTO roles (name, description, is_deleted, created_at, updated_at)
SELECT 'ROLE_SHIPPER', 'Tài xế giao hàng', FALSE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_SHIPPER');
