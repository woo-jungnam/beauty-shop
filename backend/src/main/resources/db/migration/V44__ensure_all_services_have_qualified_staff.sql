-- =========================================================================
-- V44: ENSURE EVERY ACTIVE BEAUTY SERVICE HAS FULLY QUALIFIED SPA STAFF
-- =========================================================================

-- 1. Ensure the default 3 staff members are active and marked not deleted
UPDATE staffs
SET is_active = 1, is_deleted = 0
WHERE id IN (1, 2, 3);

-- 2. Guarantee full skill certification coverage across all active services for all active staff members
INSERT IGNORE INTO staff_service_skills (staff_id, service_id, is_certified, created_by, is_deleted)
SELECT s.id, b.id, 1, 'system', 0
FROM staffs s
CROSS JOIN beauty_services b
WHERE s.is_deleted = 0 AND s.is_active = 1
  AND b.is_deleted = 0 AND b.is_active = 1
ON DUPLICATE KEY UPDATE is_certified = 1, is_deleted = 0;
