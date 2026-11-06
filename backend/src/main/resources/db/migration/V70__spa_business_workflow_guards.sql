ALTER TABLE treatment_courses ADD COLUMN paused_at DATETIME(6) NULL;

-- Preserve existing overrides. Existing appointment snapshots remain unchanged.
INSERT INTO system_configs (config_key,config_value,value_type,description,is_public,is_deleted,created_at,updated_at) VALUES
('spa.booking.policy_version','spa-v2','STRING','Spa appointment policy version',0,0,NOW(),NOW()),
('spa.booking.pending_ttl_minutes','30','INTEGER','Minutes to confirm a new pending appointment',0,0,NOW(),NOW()),
('spa.booking.cancel_cutoff_minutes','120','INTEGER','Customer cancellation cutoff in minutes',0,0,NOW(),NOW()),
('spa.booking.reschedule_cutoff_minutes','120','INTEGER','Customer reschedule cutoff in minutes',0,0,NOW(),NOW()),
('spa.booking.no_show_grace_minutes','15','INTEGER','No show grace period in minutes',0,0,NOW(),NOW()),
('spa.booking.no_show_quota_action','FORFEIT','STRING','FORFEIT or RELEASE reserved quota on no show',0,0,NOW(),NOW()),
('spa.booking.max_reschedules','3','INTEGER','Maximum customer reschedules per appointment; 0 disables limit',0,0,NOW(),NOW()),
('spa.booking.max_advance_days','90','INTEGER','Maximum booking horizon in days; 0 disables limit',0,0,NOW(),NOW()),
('spa.ticket.expiry_check_mode','SERVICE_START','STRING','Check ticket expiry at scheduled service start',0,0,NOW(),NOW())
ON DUPLICATE KEY UPDATE config_key=VALUES(config_key);

CREATE TABLE spa_package_requests (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 ticket_id BIGINT NOT NULL, user_id BIGINT NOT NULL, type VARCHAR(20) NOT NULL,
 extension_days INT NULL, reason VARCHAR(1000) NOT NULL, status VARCHAR(30) NOT NULL,
 review_reason VARCHAR(1000) NULL, reviewed_by BIGINT NULL, reviewed_at DATETIME(6) NULL,
 created_at DATETIME(6) NOT NULL,
 INDEX idx_package_request_ticket(ticket_id,status),
 CONSTRAINT fk_package_request_ticket FOREIGN KEY(ticket_id) REFERENCES user_service_tickets(id)
);
