-- Cap nhat ngay het han cho cac lo hang xa kho can date trong warehouse_stocks
-- Dam bao ngay het han nam trong khoang hop le theo InventoryExpiryPolicy (31 - 90 ngay)
UPDATE warehouse_stocks SET expiration_date = DATE_ADD(CURRENT_DATE, INTERVAL 35 DAY) WHERE product_variant_id = (SELECT id FROM product_variants WHERE sku = 'LOR-INFAL-30ML');
UPDATE warehouse_stocks SET expiration_date = DATE_ADD(CURRENT_DATE, INTERVAL 40 DAY) WHERE product_variant_id = (SELECT id FROM product_variants WHERE sku = 'LOR-HA-SERUM-30ML');
UPDATE warehouse_stocks SET expiration_date = DATE_ADD(CURRENT_DATE, INTERVAL 45 DAY) WHERE product_variant_id = (SELECT id FROM product_variants WHERE sku = 'LOR-MICELLAR-400ML');
UPDATE warehouse_stocks SET expiration_date = DATE_ADD(CURRENT_DATE, INTERVAL 50 DAY) WHERE product_variant_id = (SELECT id FROM product_variants WHERE sku = 'LRP-OIL-CORR-50ML');
UPDATE warehouse_stocks SET expiration_date = DATE_ADD(CURRENT_DATE, INTERVAL 55 DAY) WHERE product_variant_id = (SELECT id FROM product_variants WHERE sku = 'LRP-EFF-SERUM-30');
UPDATE warehouse_stocks SET expiration_date = DATE_ADD(CURRENT_DATE, INTERVAL 60 DAY) WHERE product_variant_id = (SELECT id FROM product_variants WHERE sku = 'BIO-SENSI-TON-250');
UPDATE warehouse_stocks SET expiration_date = DATE_ADD(CURRENT_DATE, INTERVAL 72 DAY) WHERE product_variant_id = (SELECT id FROM product_variants WHERE sku = 'VIC-THERMAL-300');
UPDATE warehouse_stocks SET expiration_date = DATE_ADD(CURRENT_DATE, INTERVAL 85 DAY) WHERE product_variant_id = (SELECT id FROM product_variants WHERE sku = 'CER-HYDR-CLN-473');
