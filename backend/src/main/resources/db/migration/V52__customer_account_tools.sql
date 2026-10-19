CREATE TABLE customer_skin_profiles (
 user_id BIGINT PRIMARY KEY, skin_type VARCHAR(100), sensitivity VARCHAR(100), allergies VARCHAR(1000),
 contraindications VARCHAR(1000), preferred_ingredients VARCHAR(1000), notes VARCHAR(2000), updated_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_skin_profile_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE TABLE customer_addresses (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, recipient_name VARCHAR(100) NOT NULL,
 phone VARCHAR(20) NOT NULL, address VARCHAR(500) NOT NULL, ward VARCHAR(100) NOT NULL,
 district VARCHAR(100) NOT NULL, city VARCHAR(100) NOT NULL, is_default BOOLEAN NOT NULL DEFAULT FALSE,
 updated_at DATETIME(6) NOT NULL, INDEX idx_customer_address_owner(user_id),
 CONSTRAINT fk_customer_address_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE TABLE password_recoveries (
 token_hash VARCHAR(64) PRIMARY KEY, user_id BIGINT NOT NULL, created_at DATETIME(6) NOT NULL,
 expires_at DATETIME(6) NOT NULL, used_at DATETIME(6), INDEX idx_password_recovery_owner(user_id,created_at),
 CONSTRAINT fk_password_recovery_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE TABLE newsletter_subscriptions (email VARCHAR(254) PRIMARY KEY, consented_at DATETIME(6) NOT NULL, active BOOLEAN NOT NULL);
CREATE TABLE customer_wishlist (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, product_id BIGINT NOT NULL,
 created_at DATETIME(6) NOT NULL, CONSTRAINT uk_customer_wishlist UNIQUE(user_id,product_id),
 CONSTRAINT fk_wishlist_user FOREIGN KEY(user_id) REFERENCES users(id),
 CONSTRAINT fk_wishlist_product FOREIGN KEY(product_id) REFERENCES products(id)
);
