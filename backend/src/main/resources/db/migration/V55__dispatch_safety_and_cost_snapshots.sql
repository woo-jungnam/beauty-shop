ALTER TABLE warehouse_stocks ADD COLUMN is_blocked BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE orders ADD COLUMN customer_email VARCHAR(150) NULL;
ALTER TABLE orders ADD COLUMN delivered_at DATETIME(6) NULL;
ALTER TABLE warehouse_stocks ADD COLUMN block_reason VARCHAR(500) NULL;
ALTER TABLE stock_allocations ADD COLUMN returned_quantity INT NOT NULL DEFAULT 0;
ALTER TABLE stock_allocations ADD COLUMN unit_cost_snapshot DECIMAL(12,2) NULL;
-- Historical allocations intentionally retain unknown cost. Current stock cost is not historical COGS.
CREATE TABLE batch_recalls (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,reference VARCHAR(100) NOT NULL UNIQUE,variant_id BIGINT NOT NULL,batch_code VARCHAR(100) NOT NULL,reason VARCHAR(500) NOT NULL,active BOOLEAN NOT NULL DEFAULT TRUE,closure_reason VARCHAR(500),
 created_at DATETIME(6) NOT NULL,updated_at DATETIME(6) NOT NULL,created_by VARCHAR(100),updated_by VARCHAR(100),is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE inventory_commands (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,command_key VARCHAR(100) NOT NULL UNIQUE,command_hash VARCHAR(64) NOT NULL,result_json TEXT NOT NULL,
 created_at DATETIME(6) NOT NULL,updated_at DATETIME(6) NOT NULL,created_by VARCHAR(100),updated_by VARCHAR(100),is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);
