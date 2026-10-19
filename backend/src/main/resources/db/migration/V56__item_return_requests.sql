CREATE TABLE product_returns (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,order_id BIGINT NOT NULL,status VARCHAR(20) NOT NULL,
 reason VARCHAR(1000) NOT NULL,decision_reason VARCHAR(1000),refund_amount DECIMAL(12,2) NOT NULL,
 lines_json TEXT NOT NULL,evidence_json TEXT NOT NULL,receipt_reference VARCHAR(100),refund_reference VARCHAR(100),request_key VARCHAR(100) UNIQUE,
 created_at DATETIME(6) NOT NULL,updated_at DATETIME(6) NOT NULL,created_by VARCHAR(100),updated_by VARCHAR(100),is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
 INDEX ix_product_returns_order(order_id)
);
ALTER TABLE refund_confirmations ADD COLUMN refund_kind VARCHAR(30) NOT NULL DEFAULT 'FULL';
ALTER TABLE refund_confirmations ADD COLUMN product_return_id BIGINT NULL;
ALTER TABLE product_returns ADD COLUMN replacement_order_id BIGINT NULL;
ALTER TABLE product_returns ADD COLUMN replacement_reason VARCHAR(500) NULL;
ALTER TABLE product_returns ADD COLUMN replacement_linked_at DATETIME(6) NULL;
ALTER TABLE product_returns ADD COLUMN replacement_linked_by BIGINT NULL;
