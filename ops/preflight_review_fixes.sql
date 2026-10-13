-- READ-ONLY preflight for V16-V21. Run against the database before deployment.
-- Each result set requires review; this script does not infer historical batch ownership.

SELECT session_id, COUNT(*) AS duplicate_guest_carts
FROM carts
WHERE user_id IS NULL AND session_id IS NOT NULL
GROUP BY session_id HAVING COUNT(*) > 1;

SELECT o.id, o.order_number, o.status, oi.product_variant_id, oi.quantity
FROM orders o JOIN order_items oi ON oi.order_id = o.id
WHERE o.status IN ('PENDING', 'CONFIRMED', 'PROCESSING', 'SHIPPED', 'DELIVERED')
ORDER BY o.id, oi.product_variant_id;

SELECT id, warehouse_id, product_variant_id, batch_code, quantity, reserved_quantity, expiration_date
FROM warehouse_stocks
WHERE reserved_quantity > 0 OR reserved_quantity < 0 OR quantity < reserved_quantity
ORDER BY product_variant_id, expiration_date, id;

SELECT id, order_number, user_id, service_package_id, status, payment_status
FROM orders WHERE service_package_id IS NOT NULL;

SELECT id, user_id, package_id, order_id, total_sessions, used_sessions, status
FROM user_service_tickets;

SELECT ai.ticket_id, ai.service_id, COUNT(*) AS committed_sessions
FROM appointment_items ai JOIN appointments a ON a.id = ai.appointment_id
WHERE ai.ticket_id IS NOT NULL AND a.status <> 'CANCELLED'
GROUP BY ai.ticket_id, ai.service_id;
