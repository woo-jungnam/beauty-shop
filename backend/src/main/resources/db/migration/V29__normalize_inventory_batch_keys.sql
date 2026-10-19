-- Normalize the business key without deleting or guessing how to merge stock.
-- Ambiguous legacy batches must be reconciled with their allocations and ledger.
DROP PROCEDURE IF EXISTS migrate_v29_inventory_batch_keys;
DELIMITER //
CREATE PROCEDURE migrate_v29_inventory_batch_keys()
BEGIN
    IF EXISTS (
        SELECT 1 FROM (
            SELECT warehouse_id, product_variant_id,
                   COALESCE(NULLIF(TRIM(batch_code), ''), '') normalized_batch
            FROM warehouse_stocks
            GROUP BY warehouse_id, product_variant_id, normalized_batch
            HAVING COUNT(*) > 1
        ) duplicate_batches
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Duplicate normalized stock batches: reconcile allocations and ledger before V29';
    END IF;
    UPDATE warehouse_stocks SET batch_code = COALESCE(NULLIF(TRIM(batch_code), ''), '');
    ALTER TABLE warehouse_stocks MODIFY COLUMN batch_code VARCHAR(100) NOT NULL DEFAULT '';
END //
DELIMITER ;
CALL migrate_v29_inventory_batch_keys();
DROP PROCEDURE IF EXISTS migrate_v29_inventory_batch_keys;
