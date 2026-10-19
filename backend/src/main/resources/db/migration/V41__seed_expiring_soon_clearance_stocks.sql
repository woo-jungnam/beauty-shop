-- =========================================================================
-- V41: Seed Near-Expiry Batches for Flash Sale Clearance
-- Sets upcoming expiration dates (25-85 days) and clearance discounts
-- =========================================================================

-- 1. Kem Nền L'Oreal Infallible (variant 101) - HSD còn ~25 ngày
UPDATE warehouse_stocks
SET expiration_date = DATE_ADD(CURRENT_DATE, INTERVAL 25 DAY),
    batch_code = 'LOT-CLEARANCE-25D'
WHERE warehouse_id = 1 AND product_variant_id = 101;

UPDATE product_variants
SET discount_price = 189000.00
WHERE id = 101;

-- 2. Serum L'Oreal Revitalift HA 1.5% (variant 102) - HSD còn ~32 ngày
UPDATE warehouse_stocks
SET expiration_date = DATE_ADD(CURRENT_DATE, INTERVAL 32 DAY),
    batch_code = 'LOT-CLEARANCE-32D'
WHERE warehouse_id = 1 AND product_variant_id = 102;

UPDATE product_variants
SET discount_price = 269000.00
WHERE id = 102;

-- 3. Nước Tẩy Trang L'Oreal Deep Cleansing 400ml (variant 103) - HSD còn ~40 ngày
UPDATE warehouse_stocks
SET expiration_date = DATE_ADD(CURRENT_DATE, INTERVAL 40 DAY),
    batch_code = 'LOT-CLEARANCE-40D'
WHERE warehouse_id = 1 AND product_variant_id = 103;

UPDATE product_variants
SET discount_price = 129000.00
WHERE id = 103;

-- 4. Kem Chống Nắng La Roche-Posay Anthelios Oil Correct (variant 116) - HSD còn ~45 ngày
UPDATE warehouse_stocks
SET expiration_date = DATE_ADD(CURRENT_DATE, INTERVAL 45 DAY),
    batch_code = 'LOT-CLEARANCE-45D'
WHERE warehouse_id = 1 AND product_variant_id = 116;

UPDATE product_variants
SET discount_price = 325000.00
WHERE id = 116;

-- 5. Tinh Chất La Roche-Posay Effaclar Serum 30ml (variant 118) - HSD còn ~52 ngày
UPDATE warehouse_stocks
SET expiration_date = DATE_ADD(CURRENT_DATE, INTERVAL 52 DAY),
    batch_code = 'LOT-CLEARANCE-52D'
WHERE warehouse_id = 1 AND product_variant_id = 118;

UPDATE product_variants
SET discount_price = 490000.00
WHERE id = 118;

-- 6. Nước Cân Bằng Bioderma Sensibio Tonique 250ml (variant 121) - HSD còn ~60 ngày
UPDATE warehouse_stocks
SET expiration_date = DATE_ADD(CURRENT_DATE, INTERVAL 60 DAY),
    batch_code = 'LOT-CLEARANCE-60D'
WHERE warehouse_id = 1 AND product_variant_id = 121;

UPDATE product_variants
SET discount_price = 239000.00
WHERE id = 121;

-- 7. Xịt Khoáng Vichy Mineralizing Thermal Water 300ml (variant 126) - HSD còn ~72 ngày
UPDATE warehouse_stocks
SET expiration_date = DATE_ADD(CURRENT_DATE, INTERVAL 72 DAY),
    batch_code = 'LOT-CLEARANCE-72D'
WHERE warehouse_id = 1 AND product_variant_id = 126;

UPDATE product_variants
SET discount_price = 225000.00
WHERE id = 126;

-- 8. Sữa Rửa Mặt CeraVe Hydrating Cleanser 473ml (variant 111) - HSD còn ~85 ngày
UPDATE warehouse_stocks
SET expiration_date = DATE_ADD(CURRENT_DATE, INTERVAL 85 DAY),
    batch_code = 'LOT-CLEARANCE-85D'
WHERE warehouse_id = 1 AND product_variant_id = 111;

UPDATE product_variants
SET discount_price = 289000.00
WHERE id = 111;
