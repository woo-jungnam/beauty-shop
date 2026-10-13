CREATE TABLE IF NOT EXISTS refresh_token_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    family_id CHAR(36) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6) NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_refresh_session_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_refresh_session_user (user_id),
    INDEX idx_refresh_session_family (family_id),
    INDEX idx_refresh_session_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

UPDATE product_variants variant
JOIN (
    SELECT product_id, MIN(id) AS retained_default_id
    FROM product_variants
    WHERE is_default = TRUE AND is_deleted = FALSE
    GROUP BY product_id
    HAVING COUNT(*) > 1
) duplicates ON duplicates.product_id = variant.product_id
SET variant.is_default = FALSE
WHERE variant.is_default = TRUE
  AND variant.is_deleted = FALSE
  AND variant.id <> duplicates.retained_default_id;

ALTER TABLE product_variants
    ADD COLUMN active_default_product_id BIGINT,
    ADD CONSTRAINT uk_product_variants_one_default UNIQUE (active_default_product_id),
    ADD CONSTRAINT chk_product_variant_prices
        CHECK (price >= 0 AND (discount_price IS NULL OR (discount_price >= 0 AND discount_price <= price)));

UPDATE product_variants
SET active_default_product_id = CASE WHEN is_default = TRUE AND is_deleted = FALSE THEN product_id ELSE NULL END;

CREATE TRIGGER trg_product_variants_insert
BEFORE INSERT ON product_variants
FOR EACH ROW
BEGIN
    IF NEW.is_default = TRUE AND NEW.is_deleted = FALSE THEN
        SET NEW.active_default_product_id = NEW.product_id;
    ELSE
        SET NEW.active_default_product_id = NULL;
    END IF;
END;

CREATE TRIGGER trg_product_variants_update
BEFORE UPDATE ON product_variants
FOR EACH ROW
BEGIN
    IF NEW.is_default = TRUE AND NEW.is_deleted = FALSE THEN
        SET NEW.active_default_product_id = NEW.product_id;
    ELSE
        SET NEW.active_default_product_id = NULL;
    END IF;
END;
