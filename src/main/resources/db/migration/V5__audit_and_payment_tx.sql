-- =========================================================
-- MODULE: AUDIT LOGS & PAYMENT TRANSACTIONS
-- Version: V5
-- =========================================================

-- ---------------------------------------------------------
-- TABLE: audit_logs
-- Tracks critical user and system actions
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    username VARCHAR(100),
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id VARCHAR(100),
    ip_address VARCHAR(45),
    user_agent VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    error_message TEXT,
    execution_time_ms BIGINT,
    metadata JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_user (user_id),
    INDEX idx_audit_resource (resource_type, resource_id),
    INDEX idx_audit_action (action),
    INDEX idx_audit_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------
-- TABLE: payment_transactions
-- Tracks all payment webhook and transaction histories
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS payment_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_number VARCHAR(50) NOT NULL,
    reference_code VARCHAR(100) NOT NULL UNIQUE,
    gateway VARCHAR(50) NOT NULL DEFAULT 'SEPAY',
    transfer_type VARCHAR(20) NOT NULL,
    amount DECIMAL(14, 2) NOT NULL,
    accumulated_amount DECIMAL(14, 2),
    account_number VARCHAR(50),
    sub_account VARCHAR(50),
    transaction_date VARCHAR(50),
    content VARCHAR(500),
    raw_payload JSON NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_pay_tx_order_number (order_number),
    INDEX idx_pay_tx_ref_code (reference_code),
    INDEX idx_pay_tx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
