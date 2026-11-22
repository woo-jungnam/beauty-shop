ALTER TABLE order_status_histories
    ADD COLUMN shipper_id BIGINT NULL,
    ADD COLUMN shipper_name VARCHAR(100) NULL;

CREATE INDEX idx_order_history_shipper_attempt
    ON order_status_histories (order_id, shipper_id, status, created_at);
