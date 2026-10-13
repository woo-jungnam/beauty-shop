-- Distinct operational permissions. Legacy STAFF keeps its existing manager access during rollout.
INSERT INTO roles(name,description,created_at,updated_at,is_deleted)
SELECT 'ROLE_SPA_RECEPTION','Spa appointment reception',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6),false
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name='ROLE_SPA_RECEPTION');
INSERT INTO roles(name,description,created_at,updated_at,is_deleted)
SELECT 'ROLE_SPA_THERAPIST','Assigned spa technician',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6),false
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name='ROLE_SPA_THERAPIST');

CREATE TABLE spa_form_templates (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(150) NOT NULL,
    current_version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL
);
CREATE TABLE spa_form_versions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    template_id BIGINT NOT NULL,
    version_number INT NOT NULL,
    title VARCHAR(150) NOT NULL,
    questions_json TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_spa_form_version UNIQUE (template_id,version_number),
    CONSTRAINT fk_spa_form_version_template FOREIGN KEY (template_id) REFERENCES spa_form_templates(id)
);
CREATE TABLE spa_service_preparation_policies (
    service_id BIGINT NOT NULL PRIMARY KEY,
    warnings_required BOOLEAN NOT NULL DEFAULT false,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_spa_preparation_policy_service FOREIGN KEY (service_id) REFERENCES beauty_services(id)
);
CREATE TABLE spa_service_form_requirements (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    service_id BIGINT NOT NULL,
    form_version_id BIGINT NOT NULL,
    CONSTRAINT uk_spa_service_form UNIQUE (service_id,form_version_id),
    CONSTRAINT fk_spa_service_form_service FOREIGN KEY (service_id) REFERENCES beauty_services(id),
    CONSTRAINT fk_spa_service_form_version FOREIGN KEY (form_version_id) REFERENCES spa_form_versions(id)
);
CREATE TABLE spa_preparation_snapshots (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    appointment_item_id BIGINT NOT NULL UNIQUE,
    warnings_required BOOLEAN NOT NULL DEFAULT false,
    snapshot_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_spa_preparation_item FOREIGN KEY (appointment_item_id) REFERENCES appointment_items(id)
);
CREATE TABLE spa_form_requirement_snapshots (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    preparation_id BIGINT NOT NULL,
    form_version_id BIGINT NOT NULL,
    CONSTRAINT uk_spa_snapshot_form UNIQUE (preparation_id,form_version_id),
    CONSTRAINT fk_spa_form_snapshot_preparation FOREIGN KEY (preparation_id) REFERENCES spa_preparation_snapshots(id),
    CONSTRAINT fk_spa_form_snapshot_version FOREIGN KEY (form_version_id) REFERENCES spa_form_versions(id)
);
CREATE TABLE spa_form_responses (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    requirement_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    acknowledged BOOLEAN NOT NULL,
    answers_json TEXT NOT NULL,
    submitted_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_spa_response_requirement FOREIGN KEY (requirement_id) REFERENCES spa_form_requirement_snapshots(id),
    CONSTRAINT fk_spa_response_customer FOREIGN KEY (customer_id) REFERENCES users(id),
    CONSTRAINT fk_spa_response_actor FOREIGN KEY (actor_id) REFERENCES users(id),
    CONSTRAINT ck_spa_consent_actor CHECK (customer_id=actor_id),
    CONSTRAINT ck_spa_consent_ack CHECK (acknowledged=true),
    INDEX idx_spa_response_latest (requirement_id,id)
);
CREATE TABLE spa_warning_acknowledgments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    preparation_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    summary_hash VARCHAR(64) NOT NULL,
    acknowledged_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_spa_warning_preparation FOREIGN KEY (preparation_id) REFERENCES spa_preparation_snapshots(id),
    CONSTRAINT fk_spa_warning_actor FOREIGN KEY (actor_id) REFERENCES users(id),
    INDEX idx_spa_warning_evidence (preparation_id,actor_id,summary_hash)
);
CREATE TABLE spa_preparation_overrides (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    preparation_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_spa_override_preparation FOREIGN KEY (preparation_id) REFERENCES spa_preparation_snapshots(id),
    CONSTRAINT fk_spa_override_actor FOREIGN KEY (actor_id) REFERENCES users(id),
    INDEX idx_spa_override_history (preparation_id,id)
);

CREATE TABLE spa_follow_up_instructions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    appointment_id BIGINT NOT NULL,
    appointment_item_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    due_at TIMESTAMP(6) NOT NULL,
    author_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_spa_follow_up_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(id),
    CONSTRAINT fk_spa_follow_up_item FOREIGN KEY (appointment_item_id) REFERENCES appointment_items(id),
    CONSTRAINT fk_spa_follow_up_author FOREIGN KEY (author_id) REFERENCES users(id),
    INDEX idx_spa_follow_up_due (due_at,appointment_id)
);
CREATE TABLE spa_notification_dispatches (
    dedup_key VARCHAR(180) NOT NULL PRIMARY KEY,
    appointment_id BIGINT NOT NULL,
    notification_kind VARCHAR(30) NOT NULL,
    appointment_date DATE NOT NULL,
    start_time TIME(6) NOT NULL,
    instruction_id BIGINT,
    enqueued_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_spa_dispatch_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(id),
    CONSTRAINT fk_spa_dispatch_instruction FOREIGN KEY (instruction_id) REFERENCES spa_follow_up_instructions(id),
    INDEX idx_spa_dispatch_reminder (appointment_id,notification_kind,appointment_date,start_time),
    INDEX idx_spa_dispatch_follow_up (instruction_id,notification_kind)
);
