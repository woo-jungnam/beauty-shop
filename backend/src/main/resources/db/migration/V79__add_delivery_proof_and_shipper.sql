ALTER TABLE orders
    ADD COLUMN delivery_proof_url VARCHAR(500) NULL,
    ADD COLUMN shipper_id BIGINT NULL,
    ADD COLUMN shipper_name VARCHAR(100) NULL,
    ADD COLUMN shipper_phone VARCHAR(20) NULL,
    ADD COLUMN delivered_notes VARCHAR(500) NULL;

CREATE INDEX idx_orders_shipper_status ON orders(shipper_id, status);

