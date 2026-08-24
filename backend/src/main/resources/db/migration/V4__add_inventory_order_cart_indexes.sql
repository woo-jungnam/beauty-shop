-- Migration V4: Add performance indexes for inventory, cart, and orders
-- Purpose: Optimize stock queries, cart operations, and user order listing under high load

-- 1. Index on warehouse_stocks for fast variant stock lookup and atomic updates
CREATE INDEX idx_wstock_variant ON warehouse_stocks (product_variant_id, is_deleted);

-- 2. Composite index on cart_items for fast cart lookup by cart_id and variant
CREATE INDEX idx_citem_cart_variant ON cart_items (cart_id, product_variant_id, is_deleted);

-- 3. Composite index on orders for fast user order history listing
CREATE INDEX idx_orders_user_created ON orders (user_id, created_at DESC);
