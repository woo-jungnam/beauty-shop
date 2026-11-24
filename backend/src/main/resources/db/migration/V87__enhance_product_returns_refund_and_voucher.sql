ALTER TABLE product_returns
    ADD COLUMN refund_method VARCHAR(30) NOT NULL DEFAULT 'BANK_TRANSFER',
    ADD COLUMN refund_bank_bin VARCHAR(20) NULL,
    ADD COLUMN refund_bank_name VARCHAR(100) NULL,
    ADD COLUMN refund_account_number VARCHAR(50) NULL,
    ADD COLUMN refund_account_name VARCHAR(120) NULL,
    ADD COLUMN refund_voucher_code VARCHAR(50) NULL;

ALTER TABLE vouchers
    ADD COLUMN assigned_user_id BIGINT NULL,
    ADD INDEX idx_vouchers_assigned_user (assigned_user_id);

