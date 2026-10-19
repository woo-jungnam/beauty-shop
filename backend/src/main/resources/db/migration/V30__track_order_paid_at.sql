-- The first settlement time remains unchanged through refunds and repeated delivery/webhooks.
ALTER TABLE orders
    ADD COLUMN paid_at TIMESTAMP(6) NULL,
    ADD INDEX idx_orders_paid_at (paid_at);

-- Current fresh-install V1 already includes the user-added image snapshot column; older databases may not.
DROP PROCEDURE IF EXISTS migrate_v30_order_image_snapshot;
DELIMITER //
CREATE PROCEDURE migrate_v30_order_image_snapshot()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_items' AND COLUMN_NAME = 'image_url'
    ) THEN
        ALTER TABLE order_items ADD COLUMN image_url VARCHAR(500) NULL;
    END IF;
END //
DELIMITER ;
CALL migrate_v30_order_image_snapshot();
DROP PROCEDURE IF EXISTS migrate_v30_order_image_snapshot;

-- Historical BANK settlements can only be dated when a successful incoming provider receipt exists.
UPDATE orders o
JOIN (
    SELECT order_number, MIN(created_at) settled_at
    FROM payment_transactions
    WHERE status = 'SUCCESS' AND LOWER(transfer_type) = 'in' AND amount > 0
    GROUP BY order_number
) payment ON payment.order_number = o.order_number
SET o.paid_at = payment.settled_at
WHERE o.payment_method = 'BANK' AND o.paid_amount > 0;

-- Historical COD settlement was recorded by delivery confirmation.
UPDATE orders o
JOIN (
    SELECT order_id, MIN(created_at) settled_at
    FROM order_status_histories WHERE status = 'DELIVERED'
    GROUP BY order_id
) delivery ON delivery.order_id = o.id
SET o.paid_at = delivery.settled_at
WHERE o.payment_method = 'COD' AND o.paid_amount > 0;

-- Leave NULL when there is no reliable settlement evidence; reconciliation must supply the true date.
