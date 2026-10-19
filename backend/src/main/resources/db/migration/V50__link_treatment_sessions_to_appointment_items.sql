-- Keep every appointment attempt, including cancelled/no-show visits. The course item
-- is the locked owner of its current active booking; history links are not unique.
ALTER TABLE appointment_items ADD COLUMN treatment_session_item_id BIGINT NULL;
CREATE INDEX idx_appointment_treatment_item ON appointment_items(treatment_session_item_id);
ALTER TABLE appointment_items ADD CONSTRAINT fk_appointment_treatment_item
    FOREIGN KEY (treatment_session_item_id) REFERENCES treatment_session_items(id);
