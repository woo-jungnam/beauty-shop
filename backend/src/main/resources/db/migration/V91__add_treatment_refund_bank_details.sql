ALTER TABLE treatment_workflow_requests
    ADD COLUMN refund_bank_bin VARCHAR(20) NULL,
    ADD COLUMN refund_bank_name VARCHAR(100) NULL,
    ADD COLUMN refund_account_number VARCHAR(50) NULL,
    ADD COLUMN refund_account_name VARCHAR(120) NULL;

