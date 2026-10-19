-- ====================================================================
-- MIGRATION V28: REMOVE BASE_PRICE FROM PRODUCTS AND DELEGATE TO VARIANTS
-- The product entity will no longer hold a static price column.
-- Pricing is exclusively managed inside product_variants.
-- ====================================================================

DROP PROCEDURE IF EXISTS migrate_v28_remove_product_base_price;

DELIMITER //
CREATE PROCEDURE migrate_v28_remove_product_base_price()
BEGIN
    -- 1. Ensure any product that has no variants receives a fallback default variant using its existing base_price
    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products' AND COLUMN_NAME = 'base_price'
    ) THEN
        INSERT INTO product_variants (
            product_id, sku, variant_name, price, is_default, is_active, is_deleted, created_by, updated_by
        )
        SELECT
            p.id,
            CONCAT('MIG-V28-', p.id),
            'Tiêu chuẩn',
            COALESCE(p.base_price, 0.00),
            TRUE,
            TRUE,
            FALSE,
            'system',
            'system'
        FROM products p
        WHERE NOT EXISTS (
            SELECT 1 FROM product_variants pv
            WHERE pv.product_id = p.id AND pv.is_deleted = false
        );

        -- 2. Drop the index on base_price if present
        IF EXISTS (
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products' AND INDEX_NAME = 'idx_products_base_price'
        ) THEN
            ALTER TABLE products DROP INDEX idx_products_base_price;
        END IF;

        -- 3. Drop base_price column
        ALTER TABLE products DROP COLUMN base_price;
    END IF;
END //
DELIMITER ;

CALL migrate_v28_remove_product_base_price();
DROP PROCEDURE IF EXISTS migrate_v28_remove_product_base_price;
