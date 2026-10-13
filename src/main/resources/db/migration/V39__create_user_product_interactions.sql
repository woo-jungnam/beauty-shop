-- ====================================================================
-- MIGRATION V39: CREATE USER PRODUCT INTERACTIONS TABLE FOR RECSYS
-- Bảng lưu vết hành vi người dùng (Implicit & Explicit Interactions)
-- Phục vụ thuật toán gợi ý sản phẩm Content-Based & Collaborative Filtering
-- ====================================================================

CREATE TABLE IF NOT EXISTS user_product_interactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL COMMENT 'ID người dùng đã đăng nhập (null nếu khách vãng lai)',
    session_id VARCHAR(100) NULL COMMENT 'Mã phiên ẩn danh của khách',
    product_id BIGINT NOT NULL COMMENT 'ID sản phẩm tương tác',
    action_type VARCHAR(30) NOT NULL COMMENT 'Loại hành vi: VIEW, ADD_TO_CART, PURCHASE, SEARCH_CLICK',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Thời điểm tương tác',
    INDEX idx_upi_user_action (user_id, action_type),
    INDEX idx_upi_session_action (session_id, action_type),
    INDEX idx_upi_product (product_id),
    INDEX idx_upi_created_at (created_at),
    CONSTRAINT fk_upi_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
