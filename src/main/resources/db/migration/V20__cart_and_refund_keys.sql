-- User carts are identified by user_id, never by an untrusted guest session.
UPDATE carts SET session_id = NULL WHERE user_id IS NOT NULL;
-- Deliberately fails on duplicate historical guest sessions: reconcile their contents before deployment.
ALTER TABLE carts ADD CONSTRAINT uk_cart_guest_session UNIQUE (session_id);
CREATE TABLE refund_confirmations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    bank_reference VARCHAR(100) NOT NULL UNIQUE,
    amount DECIMAL(12,2) NOT NULL,
    confirmed_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_refund_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
