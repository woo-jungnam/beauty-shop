-- Migration V3: Add covering index for is_deleted queries
-- Purpose: Fix missing index for findByIsDeletedFalse (was causing full table scan)
-- The existing idx_products_status_deleted (status, is_deleted) cannot be used
-- when query only filters on is_deleted due to MySQL leftmost prefix rule.

-- Covering index for pagination queries: WHERE is_deleted = false ORDER BY created_at DESC
CREATE INDEX idx_products_deleted_created ON products (is_deleted, created_at DESC);

-- Index for search by keyword with is_deleted filter
CREATE INDEX idx_products_deleted_name ON products (is_deleted, name);
