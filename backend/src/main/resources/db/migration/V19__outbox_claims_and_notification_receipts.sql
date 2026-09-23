ALTER TABLE outbox_messages ADD COLUMN lease_until TIMESTAMP(6) NULL, ADD COLUMN claim_token VARCHAR(36) NULL;
CREATE INDEX idx_outbox_aggregate_sequence ON outbox_messages (aggregate_type, aggregate_id, id, status);
CREATE TABLE notification_receipts (
    event_id VARCHAR(180) PRIMARY KEY,
    processed_at TIMESTAMP(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
