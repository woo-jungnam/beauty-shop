INSERT INTO roles (name, description, created_at, updated_at, is_deleted)
VALUES ('ROLE_ORDER_STAFF', 'Nhân viên xử lý đơn hàng', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), FALSE),
       ('ROLE_INVENTORY_STAFF', 'Nhân viên kho và mua hàng', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), FALSE),
       ('ROLE_CATALOG_STAFF', 'Nhân viên quản trị danh mục', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), FALSE),
       ('ROLE_CS_STAFF', 'Nhân viên chăm sóc khách hàng', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), FALSE)
ON DUPLICATE KEY UPDATE description = VALUES(description);

INSERT INTO system_configs (config_key, config_value, value_type, description, is_public, created_at, updated_at, is_deleted)
VALUES ('dashboard.monthly_revenue_target', '50000000', 'DECIMAL', 'Mục tiêu doanh thu tháng', FALSE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), FALSE),
       ('dashboard.monthly_order_target', '50', 'INTEGER', 'Mục tiêu đơn hàng tháng', FALSE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), FALSE),
       ('inventory.expiry_warning_days', '30', 'INTEGER', 'Số ngày cảnh báo cận hạn', FALSE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), FALSE)
ON DUPLICATE KEY UPDATE description = VALUES(description);

CREATE TABLE suppliers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    contact_name VARCHAR(150), email VARCHAR(150), phone VARCHAR(30), address VARCHAR(500), tax_code VARCHAR(50),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(100), updated_by VARCHAR(100), is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_suppliers_code UNIQUE (code), INDEX idx_suppliers_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE purchase_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_number VARCHAR(50) NOT NULL, supplier_id BIGINT NOT NULL, warehouse_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL, expected_date DATE, total_amount DECIMAL(14,2) NOT NULL DEFAULT 0, note VARCHAR(500),
    approved_at TIMESTAMP(6), received_at TIMESTAMP(6),
    created_by VARCHAR(100), updated_by VARCHAR(100), is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_purchase_orders_number UNIQUE (order_number),
    CONSTRAINT fk_purchase_orders_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
    CONSTRAINT fk_purchase_orders_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    INDEX idx_purchase_orders_status_date (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE purchase_order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, purchase_order_id BIGINT NOT NULL, product_variant_id BIGINT NOT NULL,
    quantity INT NOT NULL, received_quantity INT NOT NULL DEFAULT 0, unit_cost DECIMAL(12,2) NOT NULL,
    batch_code VARCHAR(100), expiration_date DATE,
    created_by VARCHAR(100), updated_by VARCHAR(100), is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_purchase_order_items_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id),
    CONSTRAINT ck_purchase_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_purchase_order_items_cost CHECK (unit_cost >= 0),
    INDEX idx_purchase_order_items_variant (product_variant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE customer_care_notes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, note_type VARCHAR(30) NOT NULL,
    content TEXT NOT NULL, skin_profile VARCHAR(1000), allergies VARCHAR(1000), contraindications VARCHAR(1000),
    follow_up_at TIMESTAMP(6),
    created_by VARCHAR(100), updated_by VARCHAR(100), is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_customer_care_notes_user FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_customer_care_notes_user_date (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
