-- Migration V2: Add indexes for product catalog performance
-- Purpose: Optimize search, filtering, and sorting queries for products

-- 1. Index for status and soft delete filter (common query condition: WHERE is_deleted = false AND status = 'ACTIVE')
CREATE INDEX idx_products_status_deleted ON products (status, is_deleted);

-- 2. Index for brand filtering with soft delete
CREATE INDEX idx_products_brand_deleted ON products (brand_id, is_deleted);

-- 3. Index for sorting by creation date (newest arrivals)
CREATE INDEX idx_products_created_at ON products (created_at);

-- 4. Index for featured products query
CREATE INDEX idx_products_featured_deleted ON products (is_featured, is_deleted);

-- 5. Index for price sorting and range filtering
CREATE INDEX idx_products_base_price ON products (base_price);

-- 6. Index for best-seller sorting
CREATE INDEX idx_products_total_sold ON products (total_sold);
