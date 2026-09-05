-- V8__add_expiration_and_membership_fields.sql
-- Add expiration date and batch code to warehouse_stocks
ALTER TABLE warehouse_stocks 
ADD COLUMN expiration_date DATE NULL,
ADD COLUMN batch_code VARCHAR(100) NULL;

-- Drop old unique constraint
ALTER TABLE warehouse_stocks DROP INDEX uk_warehouse_variant;

-- Add new unique constraint
ALTER TABLE warehouse_stocks ADD CONSTRAINT uk_warehouse_variant_batch UNIQUE (warehouse_id, product_variant_id, batch_code);

-- Add membership fields to users
ALTER TABLE users 
ADD COLUMN membership_tier VARCHAR(20) DEFAULT 'MEMBER',
ADD COLUMN loyalty_points INT DEFAULT 0;
