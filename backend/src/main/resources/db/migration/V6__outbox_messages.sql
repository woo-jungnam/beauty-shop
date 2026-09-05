-- =========================================================
-- MODULE: TRANSACTIONAL OUTBOX PATTERN
-- Version: V6
-- =========================================================

-- ---------------------------------------------------------
-- TABLE: outbox_messages
-- Guarantees At-Least-Once Delivery for event-driven messaging
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS outbox_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    topic VARCHAR(100) NOT NULL,
    message_key VARCHAR(100),
    payload JSON NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP NULL,
    INDEX idx_outbox_status_created (status, created_at),
    INDEX idx_outbox_aggregate (aggregate_type, aggregate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
