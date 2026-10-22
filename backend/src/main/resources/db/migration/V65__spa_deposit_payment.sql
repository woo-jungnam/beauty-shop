ALTER TABLE spa_appointment_finance
 ADD COLUMN deposit_transfer_code VARCHAR(39) NULL,
 ADD CONSTRAINT uk_spa_deposit_transfer UNIQUE (deposit_transfer_code);
ALTER TABLE spa_financial_entries
 MODIFY COLUMN actor_id BIGINT NULL,
 ADD COLUMN payment_transaction_id BIGINT NULL,
 ADD CONSTRAINT uk_spa_finance_transaction UNIQUE (payment_transaction_id),
 ADD CONSTRAINT fk_spa_finance_transaction FOREIGN KEY (payment_transaction_id) REFERENCES payment_transactions(id);
