ALTER TABLE orders
    ADD COLUMN shipper_cod_collected DECIMAL(12, 2) NULL,
    ADD COLUMN shipper_assigned_at DATETIME(6) NULL;
