ALTER TABLE orders
    ADD COLUMN paid_amount DECIMAL(12, 2) NOT NULL DEFAULT 0;

UPDATE orders
SET paid_amount = total_amount
WHERE payment_status = 'PAID';
