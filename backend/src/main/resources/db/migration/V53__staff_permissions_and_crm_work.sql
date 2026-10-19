CREATE TABLE staff_permissions (
 user_id BIGINT NOT NULL, permission VARCHAR(40) NOT NULL,
 PRIMARY KEY(user_id,permission),
 CONSTRAINT uk_staff_permission UNIQUE(user_id,permission),
 CONSTRAINT fk_staff_permission_user FOREIGN KEY(user_id) REFERENCES users(id)
);
-- Preserve the access of existing STAFF accounts; administrators can then narrow grants.
INSERT INTO staff_permissions(user_id,permission)
SELECT DISTINCT ur.user_id,p.permission FROM user_roles ur JOIN roles r ON r.id=ur.role_id
CROSS JOIN (SELECT 'ORDERS' permission UNION ALL SELECT 'CATALOG' UNION ALL SELECT 'INVENTORY'
 UNION ALL SELECT 'PROCUREMENT' UNION ALL SELECT 'CRM' UNION ALL SELECT 'SPA_RECEPTION'
 UNION ALL SELECT 'SPA_EXECUTION' UNION ALL SELECT 'REPORTS') p
WHERE r.name='ROLE_STAFF' AND r.is_deleted=false;
CREATE TABLE crm_work_items (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, customer_id BIGINT NOT NULL, source_note_id BIGINT UNIQUE,
 kind VARCHAR(30) NOT NULL, title VARCHAR(200) NOT NULL, description VARCHAR(10000),
 assignee_id BIGINT, due_at DATETIME(6), status VARCHAR(30) NOT NULL, outcome VARCHAR(2000),
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL, completed_at DATETIME(6),
 version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT fk_crm_work_customer FOREIGN KEY(customer_id) REFERENCES users(id),
 CONSTRAINT fk_crm_work_assignee FOREIGN KEY(assignee_id) REFERENCES users(id),
 INDEX idx_crm_work_due(status,due_at), INDEX idx_crm_work_customer(customer_id)
);
INSERT INTO crm_work_items(customer_id,source_note_id,kind,title,description,due_at,status,created_at,updated_at,version)
SELECT user_id,id,CASE WHEN note_type='COMPLAINT' THEN 'COMPLAINT' ELSE 'FOLLOW_UP' END,
 CASE WHEN note_type='COMPLAINT' THEN 'Xử lý khiếu nại' ELSE 'Liên hệ chăm sóc khách hàng' END,
 content,follow_up_at,'OPEN',created_at,updated_at,0
FROM customer_care_notes WHERE is_deleted=false AND (follow_up_at IS NOT NULL OR note_type='COMPLAINT');
