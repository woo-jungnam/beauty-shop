ALTER TABLE purchase_orders ADD COLUMN paid_amount DECIMAL(14,2) NOT NULL DEFAULT 0;
ALTER TABLE purchase_order_items ADD COLUMN returned_quantity INT NOT NULL DEFAULT 0;
CREATE TABLE procurement_movements (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,purchase_order_id BIGINT NOT NULL,reference VARCHAR(100) NOT NULL UNIQUE,kind VARCHAR(20) NOT NULL,amount DECIMAL(14,2) NOT NULL,reason VARCHAR(500) NOT NULL,lines_json TEXT NOT NULL,
 created_at DATETIME(6) NOT NULL,updated_at DATETIME(6) NOT NULL,created_by VARCHAR(100),updated_by VARCHAR(100),is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
 INDEX ix_procurement_movements_order(purchase_order_id)
);
