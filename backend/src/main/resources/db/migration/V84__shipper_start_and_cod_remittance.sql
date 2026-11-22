ALTER TABLE orders
    ADD COLUMN delivery_started_at TIMESTAMP NULL,
    ADD COLUMN shipper_cod_remitted DECIMAL(19, 2) NULL;
