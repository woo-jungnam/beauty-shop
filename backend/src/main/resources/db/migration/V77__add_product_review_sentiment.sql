ALTER TABLE product_reviews
    ADD COLUMN sentiment_state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN sentiment_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN sentiment_next_attempt_at TIMESTAMP(6) NULL,
    ADD COLUMN sentiment_model_version VARCHAR(64) NULL,
    ADD COLUMN sentiment_analyzed_at TIMESTAMP(6) NULL,
    ADD INDEX idx_product_reviews_sentiment (is_deleted, sentiment_state, sentiment_next_attempt_at, id);
