ALTER TABLE orders
    ADD COLUMN checkout_key VARCHAR(64) NULL,
    ADD COLUMN checkout_hash VARCHAR(64) NULL,
    ADD COLUMN payment_deadline TIMESTAMP(6) NULL,
    ADD COLUMN refunded_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN refund_reference VARCHAR(100) NULL,
    ADD CONSTRAINT uk_order_checkout_key UNIQUE (checkout_key),
    ADD INDEX idx_order_payment_deadline (status, payment_method, payment_deadline);

ALTER TABLE warehouse_stocks ADD COLUMN quarantined_quantity INT NOT NULL DEFAULT 0;

CREATE TABLE stock_allocations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_number VARCHAR(50) NOT NULL,
    stock_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT uk_stock_allocation_order_batch UNIQUE (order_number, stock_id),
    CONSTRAINT fk_stock_allocation_batch FOREIGN KEY (stock_id) REFERENCES warehouse_stocks(id),
    CONSTRAINT chk_stock_allocation_quantity CHECK (quantity > 0),
    INDEX idx_stock_allocation_order_variant (order_number, variant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Legacy orders deliberately receive no guessed allocations/deadlines.
-- Reconcile their real batches before fulfillment; existing reservations are preserved.
