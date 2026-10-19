-- New domain is additive. Existing packages/tickets remain ordinary prepaid rights.
ALTER TABLE beauty_services ADD COLUMN service_type VARCHAR(30) NOT NULL DEFAULT 'SINGLE_SESSION';
ALTER TABLE service_packages ADD COLUMN treatment_managed BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE treatment_templates (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    service_id BIGINT NOT NULL,
    published_version_id BIGINT NULL,
    draft_version_id BIGINT NULL,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_treatment_template_service FOREIGN KEY (service_id) REFERENCES beauty_services(id)
);
CREATE TABLE treatment_template_versions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    template_id BIGINT NOT NULL,
    version_number INT NOT NULL,
    package_id BIGINT NULL,
    plan_json LONGTEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    published_at DATETIME(6) NULL,
    CONSTRAINT uk_treatment_template_version UNIQUE(template_id,version_number),
    CONSTRAINT uk_treatment_version_package UNIQUE(package_id),
    CONSTRAINT fk_treatment_version_template FOREIGN KEY (template_id) REFERENCES treatment_templates(id),
    CONSTRAINT fk_treatment_version_package FOREIGN KEY (package_id) REFERENCES service_packages(id)
);
ALTER TABLE treatment_templates ADD CONSTRAINT fk_treatment_template_published FOREIGN KEY (published_version_id) REFERENCES treatment_template_versions(id);
ALTER TABLE treatment_templates ADD CONSTRAINT fk_treatment_template_draft FOREIGN KEY (draft_version_id) REFERENCES treatment_template_versions(id);
CREATE TABLE treatment_purchases (
    order_id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    version_id BIGINT NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    plan_json LONGTEXT NOT NULL,
    accepted_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_treatment_purchase_key UNIQUE(user_id,idempotency_key),
    CONSTRAINT fk_treatment_purchase_order FOREIGN KEY(order_id) REFERENCES orders(id),
    CONSTRAINT fk_treatment_purchase_template FOREIGN KEY(template_id) REFERENCES treatment_templates(id),
    CONSTRAINT fk_treatment_purchase_version FOREIGN KEY(version_id) REFERENCES treatment_template_versions(id)
);
CREATE TABLE treatment_courses (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    version_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    ticket_id BIGINT NOT NULL,
    assigned_staff_id BIGINT NULL,
    plan_json LONGTEXT NOT NULL,
    plan_revision INT NOT NULL DEFAULT 1,
    row_version BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL,
    expiry_date DATETIME(6) NULL,
    intake_notes TEXT NULL,
    summary TEXT NULL,
    paid_amount DECIMAL(12,2) NOT NULL,
    consumed_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    CONSTRAINT uk_treatment_course_order UNIQUE(order_id),
    CONSTRAINT uk_treatment_course_ticket UNIQUE(ticket_id),
    CONSTRAINT fk_treatment_course_template FOREIGN KEY(template_id) REFERENCES treatment_templates(id),
    CONSTRAINT fk_treatment_course_version FOREIGN KEY(version_id) REFERENCES treatment_template_versions(id),
    CONSTRAINT fk_treatment_course_order FOREIGN KEY(order_id) REFERENCES orders(id),
    CONSTRAINT fk_treatment_course_ticket FOREIGN KEY(ticket_id) REFERENCES user_service_tickets(id),
    CONSTRAINT fk_treatment_course_staff FOREIGN KEY(assigned_staff_id) REFERENCES staffs(id)
);
CREATE INDEX idx_treatment_course_user ON treatment_courses(user_id,created_at);
CREATE INDEX idx_treatment_course_intake ON treatment_courses(status,assigned_staff_id);
CREATE TABLE treatment_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    sequence_number INT NOT NULL,
    plan_revision INT NOT NULL DEFAULT 1,
    retired BOOLEAN NOT NULL DEFAULT FALSE,
    title VARCHAR(150) NOT NULL,
    min_gap_days INT NOT NULL,
    max_gap_days INT NULL,
    is_required BOOLEAN NOT NULL,
    status VARCHAR(30) NOT NULL,
    completed_at DATETIME(6) NULL,
    result_text TEXT NULL,
    aftercare TEXT NULL,
    internal_notes TEXT NULL,
    shared_with_customer BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_treatment_session_revision UNIQUE(course_id,sequence_number,plan_revision),
    CONSTRAINT fk_treatment_session_course FOREIGN KEY(course_id) REFERENCES treatment_courses(id)
);
CREATE TABLE treatment_session_items (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    retired BOOLEAN NOT NULL DEFAULT FALSE,
    service_id BIGINT NOT NULL,
    service_name VARCHAR(150) NOT NULL,
    is_required BOOLEAN NOT NULL,
    status VARCHAR(30) NOT NULL,
    appointment_item_id BIGINT NULL,
    completed_at DATETIME(6) NULL,
    allocated_amount DECIMAL(12,2) NOT NULL,
    CONSTRAINT uk_treatment_item_appointment UNIQUE(appointment_item_id),
    CONSTRAINT fk_treatment_item_session FOREIGN KEY(session_id) REFERENCES treatment_sessions(id),
    CONSTRAINT fk_treatment_item_course FOREIGN KEY(course_id) REFERENCES treatment_courses(id),
    CONSTRAINT fk_treatment_item_service FOREIGN KEY(service_id) REFERENCES beauty_services(id),
    CONSTRAINT fk_treatment_item_appointment FOREIGN KEY(appointment_item_id) REFERENCES appointment_items(id)
);
CREATE INDEX idx_treatment_item_course ON treatment_session_items(course_id,status);
CREATE TABLE treatment_session_results (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    result_text TEXT NOT NULL,
    aftercare TEXT NULL,
    internal_notes TEXT NULL,
    shared_with_customer BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_treatment_result_session FOREIGN KEY(session_id) REFERENCES treatment_sessions(id)
);
CREATE TABLE treatment_financial_consumptions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    appointment_item_id BIGINT NOT NULL,
    session_item_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    kind VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_treatment_consumption_item UNIQUE(appointment_item_id),
    CONSTRAINT uk_treatment_consumption_session_item UNIQUE(session_item_id),
    CONSTRAINT fk_treatment_consumption_course FOREIGN KEY(course_id) REFERENCES treatment_courses(id),
    CONSTRAINT fk_treatment_consumption_item FOREIGN KEY(appointment_item_id) REFERENCES appointment_items(id),
    CONSTRAINT fk_treatment_consumption_session_item FOREIGN KEY(session_item_id) REFERENCES treatment_session_items(id)
);
CREATE TABLE treatment_changes (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    base_revision INT NOT NULL,
    plan_json LONGTEXT NOT NULL,
    old_plan_json LONGTEXT NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(30) NOT NULL,
    price_difference DECIMAL(12,2) NOT NULL,
    proposed_by BIGINT NOT NULL,
    approved_by BIGINT NULL,
    accepted_by BIGINT NULL,
    settlement_order_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    accepted_at DATETIME(6) NULL,
    applied_at DATETIME(6) NULL,
    CONSTRAINT fk_treatment_change_course FOREIGN KEY(course_id) REFERENCES treatment_courses(id),
    CONSTRAINT fk_treatment_change_order FOREIGN KEY(settlement_order_id) REFERENCES orders(id)
);
CREATE TABLE treatment_workflow_requests (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    extension_days INT NULL,
    requested_by BIGINT NOT NULL,
    reviewed_by BIGINT NULL,
    review_reason VARCHAR(1000) NULL,
    refund_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    refund_reference VARCHAR(100) NULL,
    created_at DATETIME(6) NOT NULL,
    reviewed_at DATETIME(6) NULL,
    CONSTRAINT fk_treatment_request_course FOREIGN KEY(course_id) REFERENCES treatment_courses(id)
);
CREATE INDEX idx_treatment_request_queue ON treatment_workflow_requests(status,type);
CREATE TABLE treatment_audit (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    actor_id BIGINT NULL,
    action VARCHAR(60) NOT NULL,
    details TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_treatment_audit_course FOREIGN KEY(course_id) REFERENCES treatment_courses(id)
);
CREATE INDEX idx_treatment_audit_course ON treatment_audit(course_id,id);
