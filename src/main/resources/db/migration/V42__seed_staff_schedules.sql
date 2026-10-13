-- =========================================================================
-- V42: SEED DEFAULT WORKING SHIFTS FOR SPA STAFF MEMBERS
-- =========================================================================

-- Create standard daily shifts (08:00:00 - 20:00:00) for all active staff
-- from 2026-10-01 to 2027-04-01 (180 days)
INSERT IGNORE INTO staff_schedules (staff_id, work_date, start_time, end_time, status, note, created_by, is_deleted)
SELECT
    s.id AS staff_id,
    d.work_date,
    '08:00:00' AS start_time,
    '20:00:00' AS end_time,
    'SCHEDULED' AS status,
    'Ca làm việc tiêu chuẩn' AS note,
    'system' AS created_by,
    0 AS is_deleted
FROM staffs s
CROSS JOIN (
    WITH RECURSIVE dates AS (
        SELECT DATE('2026-10-01') AS work_date
        UNION ALL
        SELECT DATE_ADD(work_date, INTERVAL 1 DAY)
        FROM dates
        WHERE work_date < DATE('2027-04-01')
    )
    SELECT work_date FROM dates
) d
WHERE s.is_deleted = 0 AND s.is_active = 1;
