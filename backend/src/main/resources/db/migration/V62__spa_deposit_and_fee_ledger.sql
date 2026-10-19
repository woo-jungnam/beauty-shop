CREATE TABLE spa_financial_policy (
 id BIGINT PRIMARY KEY, version INTEGER NOT NULL,
 deposit_amount DECIMAL(12,0) NOT NULL DEFAULT 0,
 cancellation_fee DECIMAL(12,0) NOT NULL DEFAULT 0,
 no_show_fee DECIMAL(12,0) NOT NULL DEFAULT 0
);
INSERT INTO spa_financial_policy(id,version,deposit_amount,cancellation_fee,no_show_fee) VALUES(1,1,0,0,0);
CREATE TABLE spa_appointment_finance (
 appointment_id BIGINT PRIMARY KEY, policy_version INTEGER NOT NULL,
 deposit_required DECIMAL(12,0) NOT NULL, cancellation_fee DECIMAL(12,0) NOT NULL,
 no_show_fee DECIMAL(12,0) NOT NULL, assessed_fee DECIMAL(12,0) NOT NULL DEFAULT 0,
 accepted_by BIGINT, accepted_at TIMESTAMP(6) NULL, closed BOOLEAN NOT NULL DEFAULT FALSE,
 CONSTRAINT fk_spa_finance_appointment FOREIGN KEY(appointment_id) REFERENCES appointments(id),
 CONSTRAINT fk_spa_finance_acceptor FOREIGN KEY(accepted_by) REFERENCES users(id)
);
CREATE TABLE spa_financial_entries (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, appointment_id BIGINT NOT NULL,
 entry_key VARCHAR(128) NOT NULL UNIQUE, kind VARCHAR(20) NOT NULL,
 amount DECIMAL(12,0) NOT NULL, method VARCHAR(20) NOT NULL,
 external_reference VARCHAR(128), reason VARCHAR(500) NOT NULL,
 actor_id BIGINT NOT NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 INDEX idx_spa_finance_appointment(appointment_id,id),
 UNIQUE KEY uk_spa_finance_evidence(method,external_reference),
 CONSTRAINT fk_spa_finance_entry_appointment FOREIGN KEY(appointment_id) REFERENCES appointments(id),
 CONSTRAINT fk_spa_finance_entry_actor FOREIGN KEY(actor_id) REFERENCES users(id)
);
CREATE TABLE spa_invoice_advances (
 reference VARCHAR(128) PRIMARY KEY, order_id BIGINT NOT NULL, amount DECIMAL(12,0) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_spa_invoice_advance_order FOREIGN KEY(order_id) REFERENCES orders(id)
);
