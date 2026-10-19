-- ====================================================================
-- MIGRATION V38: NORMALIZE SKIN_TYPE 'ALL' TO 'ALL_SKIN'
-- Fixes IllegalArgumentException when loading legacy demo products
-- ====================================================================

UPDATE products
SET skin_type = 'ALL_SKIN'
WHERE skin_type = 'ALL';
