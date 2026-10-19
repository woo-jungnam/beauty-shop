CREATE TABLE inventory_consumptions (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, reference VARCHAR(100) NOT NULL UNIQUE,
 variant_id BIGINT NOT NULL, warehouse_id BIGINT NOT NULL, quantity INT NOT NULL,
 total_cost DECIMAL(14,2) NOT NULL, batch_snapshot TEXT NOT NULL,
 created_at DATETIME(6) NOT NULL,updated_at DATETIME(6) NOT NULL,created_by VARCHAR(100),updated_by VARCHAR(100),is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);
