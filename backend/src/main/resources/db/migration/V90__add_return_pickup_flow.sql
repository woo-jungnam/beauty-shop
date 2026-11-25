ALTER TABLE product_returns
    ADD COLUMN return_method VARCHAR(30) NOT NULL DEFAULT 'SELF_SHIP',
    ADD COLUMN pickup_shipper_id BIGINT NULL,
    ADD COLUMN pickup_shipper_name VARCHAR(100) NULL,
    ADD COLUMN pickup_shipper_phone VARCHAR(20) NULL,
    ADD COLUMN pickup_assigned_at DATETIME(6) NULL,
    ADD COLUMN pickup_started_at DATETIME(6) NULL,
    ADD COLUMN picked_up_at DATETIME(6) NULL,
    ADD COLUMN pickup_proof_url VARCHAR(500) NULL,
    ADD COLUMN pickup_notes VARCHAR(500) NULL,
    ADD COLUMN pickup_failure_reason VARCHAR(500) NULL,
    ADD COLUMN pickup_attempt_count INT NOT NULL DEFAULT 0,
    ADD COLUMN handover_requested_at DATETIME(6) NULL,
    ADD COLUMN handover_notes VARCHAR(500) NULL,
    ADD COLUMN received_at DATETIME(6) NULL,
    ADD COLUMN received_by BIGINT NULL,
    ADD INDEX ix_product_returns_pickup_shipper (pickup_shipper_id, status);

