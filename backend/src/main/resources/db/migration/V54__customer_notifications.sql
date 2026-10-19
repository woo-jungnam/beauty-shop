CREATE TABLE customer_notifications (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, event_key VARCHAR(160) NOT NULL,
 type VARCHAR(30) NOT NULL, title VARCHAR(200) NOT NULL, body VARCHAR(2000) NOT NULL, target VARCHAR(300),
 created_at DATETIME(6) NOT NULL, read_at DATETIME(6),
 CONSTRAINT uk_customer_notification_event UNIQUE(user_id,event_key),
 CONSTRAINT fk_customer_notification_user FOREIGN KEY(user_id) REFERENCES users(id),
 INDEX idx_customer_notification_owner(user_id,id)
);
INSERT INTO customer_notifications(user_id,event_key,type,title,body,target,created_at,read_at)
SELECT user_id,CONCAT('treatment:',event_key),'TREATMENT',title,message,
 CONCAT('treatments?course=',course_id),created_at,read_at FROM treatment_notifications;
