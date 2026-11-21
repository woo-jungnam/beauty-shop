START TRANSACTION;

-- An administrator sets the initial password through the account reset action.
INSERT INTO users (username, full_name, status, is_deleted)
SELECT 'shipper01', 'Tài xế giao hàng 01', 'ACTIVE', FALSE
WHERE EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_SHIPPER' AND is_deleted = FALSE)
  AND NOT EXISTS (SELECT 1 FROM users WHERE username = 'shipper01');

-- Link the role only to the account created by this migration.
SET @new_shipper_id = IF(ROW_COUNT() = 1, LAST_INSERT_ID(), NULL);

INSERT INTO user_roles (user_id, role_id)
SELECT @new_shipper_id, id
FROM roles
WHERE name = 'ROLE_SHIPPER' AND is_deleted = FALSE AND @new_shipper_id IS NOT NULL;

COMMIT;
