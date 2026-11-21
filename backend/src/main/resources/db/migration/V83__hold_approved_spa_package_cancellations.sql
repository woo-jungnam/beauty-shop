UPDATE user_service_tickets t
JOIN spa_package_requests r ON r.ticket_id = t.id AND r.status = 'AWAITING_SETTLEMENT'
SET t.status = 'AWAITING_SETTLEMENT'
WHERE t.status <> 'REVOKED';
