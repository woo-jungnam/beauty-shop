-- =========================================================================
-- V68: FLASH SALE CAMPAIGNS AND TIME-SLOT QUOTA ENGINE
-- =========================================================================

-- 1. Bảng chiến dịch Flash Sale
CREATE TABLE IF NOT EXISTS flash_sale_campaigns (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    banner_url VARCHAR(500),
    starts_at TIMESTAMP NOT NULL,
    ends_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED', -- DRAFT, SCHEDULED, ACTIVE, ENDED, PAUSED
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_flash_sale_window (starts_at, ends_at, status, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. Bảng sản phẩm / biến thể SKU tham gia Flash Sale
CREATE TABLE IF NOT EXISTS flash_sale_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    campaign_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_variant_id BIGINT NOT NULL,
    flash_price DECIMAL(15, 2) NOT NULL,
    quantity_quota INT NOT NULL,          -- Tổng số suất bán ra trong đợt sale
    quantity_sold INT NOT NULL DEFAULT 0,  -- Số suất đã bán
    user_limit INT NOT NULL DEFAULT 2,     -- Giới hạn mua mỗi user
    display_order INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_flash_item_campaign FOREIGN KEY (campaign_id) REFERENCES flash_sale_campaigns(id) ON DELETE CASCADE,
    CONSTRAINT fk_flash_item_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_flash_item_variant FOREIGN KEY (product_variant_id) REFERENCES product_variants(id),
    CONSTRAINT uk_flash_campaign_variant UNIQUE (campaign_id, product_variant_id),
    CONSTRAINT chk_flash_quota CHECK (quantity_sold <= quantity_quota)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. Bảng giữ chỗ suất mua Flash Sale theo đơn hàng
CREATE TABLE IF NOT EXISTS flash_sale_order_allocations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    flash_sale_item_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    allocated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMP NULL,
    INDEX idx_flash_alloc_order (order_id),
    INDEX idx_flash_alloc_item (flash_sale_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
