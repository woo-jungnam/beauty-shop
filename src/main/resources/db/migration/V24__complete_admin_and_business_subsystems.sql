-- Complete admin and business subsystems.

ALTER TABLE users
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD INDEX idx_users_status_deleted (status, is_deleted);

CREATE TABLE user_status_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    old_status VARCHAR(20) NOT NULL,
    new_status VARCHAR(20) NOT NULL,
    reason VARCHAR(500),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_user_status_history_user FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_user_status_history_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE warehouse_stocks
    ADD COLUMN cost_price DECIMAL(12,2) NOT NULL DEFAULT 0;

ALTER TABLE orders
    ADD COLUMN carrier_name VARCHAR(100) NULL,
    ADD COLUMN tracking_code VARCHAR(100) NULL,
    ADD COLUMN cancel_reason VARCHAR(500) NULL,
    ADD COLUMN cancelled_by BIGINT NULL,
    ADD INDEX idx_orders_admin_search (status, payment_status, created_at),
    ADD INDEX idx_orders_customer_phone (customer_phone),
    ADD INDEX idx_orders_tracking_code (tracking_code),
    ADD CONSTRAINT fk_orders_cancelled_by FOREIGN KEY (cancelled_by) REFERENCES users(id) ON DELETE SET NULL;

CREATE TABLE vouchers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    discount_type VARCHAR(20) NOT NULL,
    discount_value DECIMAL(12,2) NOT NULL,
    max_discount_amount DECIMAL(12,2),
    min_order_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    starts_at TIMESTAMP(6) NOT NULL,
    ends_at TIMESTAMP(6) NOT NULL,
    usage_limit INT,
    per_user_limit INT NOT NULL DEFAULT 1,
    used_count INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_vouchers_code UNIQUE (code),
    CONSTRAINT chk_vouchers_value CHECK (discount_value > 0),
    CONSTRAINT chk_vouchers_dates CHECK (ends_at > starts_at),
    CONSTRAINT chk_vouchers_limits CHECK ((usage_limit IS NULL OR usage_limit > 0) AND per_user_limit > 0),
    INDEX idx_vouchers_active_period (is_active, starts_at, ends_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE orders
    ADD COLUMN voucher_id BIGINT NULL,
    ADD INDEX idx_orders_voucher (voucher_id),
    ADD CONSTRAINT fk_orders_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers(id) ON DELETE SET NULL;

CREATE TABLE voucher_usages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    voucher_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    discount_amount DECIMAL(12,2) NOT NULL,
    used_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_voucher_usage_order UNIQUE (order_id),
    CONSTRAINT fk_voucher_usage_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers(id),
    CONSTRAINT fk_voucher_usage_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_voucher_usage_order FOREIGN KEY (order_id) REFERENCES orders(id),
    INDEX idx_voucher_usage_user (voucher_id, user_id, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE product_reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    rating TINYINT NOT NULL,
    title VARCHAR(150),
    content VARCHAR(2000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    admin_reply VARCHAR(2000),
    replied_at TIMESTAMP(6) NULL,
    approved_at TIMESTAMP(6) NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_product_review_purchase UNIQUE (product_id, user_id, order_id),
    CONSTRAINT fk_product_review_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_product_review_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_product_review_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT chk_product_review_rating CHECK (rating BETWEEN 1 AND 5),
    INDEX idx_product_reviews_public (product_id, status, created_at),
    INDEX idx_product_reviews_admin (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE inventory_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    warehouse_stock_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    product_variant_id BIGINT NOT NULL,
    transaction_type VARCHAR(30) NOT NULL,
    quantity INT NOT NULL,
    quantity_before INT NOT NULL,
    quantity_after INT NOT NULL,
    unit_cost DECIMAL(12,2) NOT NULL DEFAULT 0,
    reference_type VARCHAR(30),
    reference_id VARCHAR(100),
    note VARCHAR(500),
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_inventory_tx_stock FOREIGN KEY (warehouse_stock_id) REFERENCES warehouse_stocks(id),
    CONSTRAINT fk_inventory_tx_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_inventory_tx_variant FOREIGN KEY (product_variant_id) REFERENCES product_variants(id),
    CONSTRAINT chk_inventory_tx_quantity CHECK (quantity <> 0),
    INDEX idx_inventory_tx_stock_time (warehouse_stock_id, occurred_at),
    INDEX idx_inventory_tx_variant_time (product_variant_id, occurred_at),
    INDEX idx_inventory_tx_reference (reference_type, reference_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE staff_schedules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    staff_id BIGINT NOT NULL,
    work_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    note VARCHAR(500),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_staff_schedule_staff FOREIGN KEY (staff_id) REFERENCES staffs(id) ON DELETE CASCADE,
    CONSTRAINT chk_staff_schedule_time CHECK (end_time > start_time),
    INDEX idx_staff_schedule_lookup (staff_id, work_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE system_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL,
    config_value TEXT NOT NULL,
    value_type VARCHAR(20) NOT NULL DEFAULT 'STRING',
    description VARCHAR(500),
    is_public BOOLEAN NOT NULL DEFAULT FALSE,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_system_configs_key UNIQUE (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE media_assets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    original_name VARCHAR(255) NOT NULL,
    stored_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    public_url VARCHAR(500) NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_media_assets_stored_name UNIQUE (stored_name),
    INDEX idx_media_assets_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
