-- Cap nhat ngay het han cho cac lo hang xa kho can date trong warehouse_stocks
-- Dam bao ngay het han nam trong khoang hop le theo InventoryExpiryPolicy (31 - 90 ngay)
UPDATE warehouse_stocks SET expiration_date = DATE_ADD(CURRENT_DATE, INTERVAL 35 DAY) WHERE product_variant_id = 101;
UPDATE warehouse_stocks SET expiration_date = DATE_ADD(CURRENT_DATE, INTERVAL 40 DAY) WHERE product_variant_id = 102;
UPDATE warehouse_stocks SET expiration_date = DATE_ADD(CURRENT_DATE, INTERVAL 45 DAY) WHERE product_variant_id = 103;
UPDATE warehouse_stocks SET expiration_date = DATE_ADD(CURRENT_DATE, INTERVAL 50 DAY) WHERE product_variant_id = 116;
UPDATE warehouse_stocks SET expiration_date = DATE_ADD(CURRENT_DATE, INTERVAL 55 DAY) WHERE product_variant_id = 118;
UPDATE warehouse_stocks SET expiration_date = DATE_ADD(CURRENT_DATE, INTERVAL 60 DAY) WHERE product_variant_id = 121;
UPDATE warehouse_stocks SET expiration_date = DATE_ADD(CURRENT_DATE, INTERVAL 72 DAY) WHERE product_variant_id = 126;
UPDATE warehouse_stocks SET expiration_date = DATE_ADD(CURRENT_DATE, INTERVAL 85 DAY) WHERE product_variant_id = 111;
