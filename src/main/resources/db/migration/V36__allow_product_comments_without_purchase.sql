ALTER TABLE product_reviews ADD COLUMN is_verified_purchase BOOLEAN NOT NULL DEFAULT FALSE AFTER rating;

UPDATE product_reviews
SET is_verified_purchase = TRUE
WHERE order_id IS NOT NULL AND rating IS NOT NULL;

ALTER TABLE product_reviews DROP FOREIGN KEY fk_product_review_order;
ALTER TABLE product_reviews DROP CHECK chk_product_review_rating;

ALTER TABLE product_reviews MODIFY COLUMN order_id BIGINT NULL;
ALTER TABLE product_reviews MODIFY COLUMN rating TINYINT NULL;

ALTER TABLE product_reviews
    ADD CONSTRAINT fk_product_review_order FOREIGN KEY (order_id) REFERENCES orders(id),
    ADD CONSTRAINT chk_product_review_rating CHECK (rating IS NULL OR rating BETWEEN 1 AND 5);
