-- Simplify treatment courses so paid courses are immediately active and completed visits finalize sessions without blocking on clinical write-up.
ALTER TABLE treatment_courses MODIFY COLUMN status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE';

UPDATE treatment_courses
SET status = 'ACTIVE'
WHERE status = 'PENDING_INTAKE';

UPDATE treatment_sessions s
SET status = 'COMPLETED',
    completed_at = COALESCE(s.completed_at, NOW(6))
WHERE s.status = 'AWAITING_RESULT'
  AND NOT EXISTS (
    SELECT 1
    FROM treatment_session_items i
    WHERE i.session_id = s.id
      AND i.retired = FALSE
      AND i.is_required = TRUE
      AND i.status NOT IN ('COMPLETED', 'NO_SHOW')
  );

