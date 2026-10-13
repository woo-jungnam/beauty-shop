-- Stop before changing counters when historic ticket reservations cannot be reconciled.
DROP PROCEDURE IF EXISTS validate_spa_reservations_v31;
DELIMITER $$
CREATE PROCEDURE validate_spa_reservations_v31()
BEGIN
    IF EXISTS (SELECT 1 FROM user_service_tickets WHERE total_sessions <= 0 OR used_sessions < 0 OR used_sessions > total_sessions)
       OR EXISTS (SELECT 1 FROM ticket_entitlements WHERE total_sessions <= 0 OR used_sessions < 0 OR used_sessions > total_sessions)
       OR EXISTS (SELECT 1 FROM user_service_tickets t JOIN (
          SELECT ticket_id, SUM(total_sessions) total, SUM(used_sessions) used FROM ticket_entitlements GROUP BY ticket_id
       ) e ON e.ticket_id=t.id WHERE t.total_sessions <> e.total OR t.used_sessions <> e.used) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V31: reconcile historical ticket quota totals before migration';
    END IF;
    IF EXISTS (
        SELECT 1 FROM appointment_items i JOIN appointments a ON a.id = i.appointment_id
        LEFT JOIN ticket_entitlements e ON e.ticket_id = i.ticket_id AND e.service_id = i.service_id
        WHERE i.ticket_id IS NOT NULL AND i.is_deleted = FALSE AND a.is_deleted = FALSE
          AND a.status IN ('PENDING','CONFIRMED','IN_PROGRESS')
        GROUP BY i.ticket_id, i.service_id, e.used_sessions
        HAVING e.used_sessions IS NULL OR COUNT(*) > e.used_sessions
    ) OR EXISTS (
        SELECT 1 FROM appointment_items i JOIN appointments a ON a.id = i.appointment_id
        JOIN user_service_tickets t ON t.id = i.ticket_id
        WHERE i.is_deleted = FALSE AND a.is_deleted = FALSE AND a.status IN ('PENDING','CONFIRMED','IN_PROGRESS')
        GROUP BY i.ticket_id, t.used_sessions HAVING COUNT(*) > t.used_sessions
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V31: reconcile historical ticket reservations before migration';
    END IF;
END$$
DELIMITER ;
CALL validate_spa_reservations_v31();
DROP PROCEDURE validate_spa_reservations_v31;

ALTER TABLE appointments
    ADD COLUMN checked_in_at TIMESTAMP(6) NULL,
    ADD COLUMN checked_in_by_user_id BIGINT NULL,
    ADD COLUMN actual_started_at TIMESTAMP(6) NULL,
    ADD COLUMN actual_completed_at TIMESTAMP(6) NULL,
    ADD COLUMN pending_expires_at TIMESTAMP(6) NULL,
    ADD COLUMN policy_snapshot TEXT NULL,
    ADD INDEX idx_pending_confirmation (status, pending_expires_at, id);
ALTER TABLE appointment_items
    ADD COLUMN service_name_snapshot VARCHAR(150) NULL,
    ADD COLUMN execution_status VARCHAR(30) NOT NULL DEFAULT 'PLANNED',
    ADD COLUMN ticket_usage_state VARCHAR(30) NOT NULL DEFAULT 'NONE',
    ADD COLUMN actual_started_at TIMESTAMP(6) NULL,
    ADD COLUMN actual_completed_at TIMESTAMP(6) NULL,
    ADD COLUMN performed_by_user_id BIGINT NULL,
    ADD COLUMN execution_notes VARCHAR(2000) NULL;
ALTER TABLE user_service_tickets
    ADD COLUMN reserved_sessions INT NOT NULL DEFAULT 0,
    ADD COLUMN expiry_check_mode VARCHAR(30) NOT NULL DEFAULT 'BOOKING_TIME';
ALTER TABLE ticket_entitlements ADD COLUMN reserved_sessions INT NOT NULL DEFAULT 0;
ALTER TABLE spa_purchase_snapshots ADD COLUMN expiry_check_mode VARCHAR(30) NOT NULL DEFAULT 'BOOKING_TIME';

CREATE TABLE ticket_session_movements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT NOT NULL, service_id BIGINT NULL, appointment_item_id BIGINT NULL,
    operation VARCHAR(30) NOT NULL,
    used_after INT NOT NULL, reserved_after INT NOT NULL, available_after INT NOT NULL,
    actor_user_id BIGINT NULL, reason VARCHAR(2000) NULL,
    idempotency_key VARCHAR(150) NOT NULL UNIQUE,
    command_fingerprint VARCHAR(64) NULL, session_adjustment INT NULL,
    expiry_before TIMESTAMP(6) NULL, expiry_after TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(100), updated_by VARCHAR(100), is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    INDEX idx_ticket_movements (ticket_id, id),
    FOREIGN KEY (ticket_id) REFERENCES user_service_tickets(id),
    FOREIGN KEY (appointment_item_id) REFERENCES appointment_items(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE appointment_action_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, appointment_id BIGINT NOT NULL, appointment_item_id BIGINT NULL,
    actor_user_id BIGINT NULL, action VARCHAR(40) NOT NULL,
    from_state VARCHAR(40) NULL, to_state VARCHAR(40) NULL, details VARCHAR(2000) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(100), updated_by VARCHAR(100), is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    INDEX idx_appointment_actions (appointment_id, id),
    FOREIGN KEY (appointment_id) REFERENCES appointments(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Old booking deducted used at reservation time. Move only provable active reservations.
UPDATE ticket_entitlements e JOIN (
    SELECT i.ticket_id, i.service_id, COUNT(*) n
    FROM appointment_items i JOIN appointments a ON a.id = i.appointment_id
    WHERE i.ticket_id IS NOT NULL AND i.is_deleted = FALSE AND a.is_deleted = FALSE
      AND a.status IN ('PENDING','CONFIRMED','IN_PROGRESS') GROUP BY i.ticket_id, i.service_id
) r ON r.ticket_id = e.ticket_id AND r.service_id = e.service_id
SET e.reserved_sessions = r.n, e.used_sessions = e.used_sessions - r.n;
UPDATE user_service_tickets t JOIN (
    SELECT ticket_id, SUM(reserved_sessions) n FROM ticket_entitlements GROUP BY ticket_id
) r ON r.ticket_id = t.id
SET t.reserved_sessions = r.n, t.used_sessions = t.used_sessions - r.n;
UPDATE user_service_tickets SET status = IF(expiry_date IS NULL OR expiry_date > CURRENT_TIMESTAMP, 'ACTIVE','EXPIRED')
WHERE status = 'COMPLETED' AND reserved_sessions > 0 AND used_sessions < total_sessions;
UPDATE appointment_items i JOIN appointments a ON a.id = i.appointment_id
SET i.ticket_usage_state = 'RESERVED'
WHERE i.ticket_id IS NOT NULL AND i.is_deleted = FALSE AND a.is_deleted = FALSE
  AND a.status IN ('PENDING','CONFIRMED','IN_PROGRESS');
-- Do not fabricate per-service performance, performer, duration, or historical consent.
UPDATE appointment_items i JOIN appointments a ON a.id = i.appointment_id
SET i.execution_status = 'LEGACY_FINALIZED',
    i.ticket_usage_state = IF(i.ticket_id IS NULL, 'NONE', 'LEGACY_FINALIZED')
WHERE a.status IN ('COMPLETED','CANCELLED','NO_SHOW');
INSERT INTO ticket_session_movements (ticket_id, operation, used_after, reserved_after, available_after, reason, idempotency_key)
SELECT id, 'LEGACY_ADJUSTMENT', used_sessions, reserved_sessions, total_sessions-used_sessions-reserved_sessions,
       'V31 opening balance: historical usage is not proof of service performance', CONCAT('V31:TICKET:', id)
FROM user_service_tickets;
ALTER TABLE user_service_tickets ADD CONSTRAINT chk_ticket_quota_v31 CHECK
    (used_sessions >= 0 AND reserved_sessions >= 0 AND used_sessions + reserved_sessions <= total_sessions);
ALTER TABLE ticket_entitlements ADD CONSTRAINT chk_entitlement_quota_v31 CHECK
    (used_sessions >= 0 AND reserved_sessions >= 0 AND used_sessions + reserved_sessions <= total_sessions);
