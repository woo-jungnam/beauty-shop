CREATE TABLE treatment_notifications (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    event_key VARCHAR(150) NOT NULL,
    kind VARCHAR(60) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    read_at TIMESTAMP(6) NULL,
    CONSTRAINT uk_treatment_notification_event UNIQUE(event_key),
    CONSTRAINT fk_treatment_notification_user FOREIGN KEY(user_id) REFERENCES users(id),
    CONSTRAINT fk_treatment_notification_course FOREIGN KEY(course_id) REFERENCES treatment_courses(id),
    INDEX idx_treatment_notification_owner(user_id, id)
);
