ALTER TABLE spa_purchase_snapshots ADD COLUMN package_name VARCHAR(255) NULL;
ALTER TABLE spa_purchase_snapshots ADD COLUMN purchase_amount DECIMAL(19,2) NULL;
CREATE TABLE spa_purchase_service_details (
 order_id BIGINT NOT NULL, service_id BIGINT NOT NULL, service_name VARCHAR(255) NOT NULL,
 unit_value DECIMAL(19,2) NOT NULL, PRIMARY KEY(order_id,service_id),
 CONSTRAINT fk_spa_purchase_detail_snapshot FOREIGN KEY(order_id) REFERENCES spa_purchase_snapshots(order_id)
);
ALTER TABLE user_service_tickets ADD COLUMN package_name_snapshot VARCHAR(255) NULL;
ALTER TABLE user_service_tickets ADD COLUMN treatment_course_id BIGINT NULL;
CREATE TABLE ticket_service_names (
 ticket_id BIGINT NOT NULL, service_id BIGINT NOT NULL, service_name VARCHAR(255) NOT NULL,
 PRIMARY KEY(ticket_id,service_id), CONSTRAINT fk_ticket_name_ticket FOREIGN KEY(ticket_id) REFERENCES user_service_tickets(id)
);
UPDATE user_service_tickets t JOIN treatment_courses c ON c.ticket_id=t.id SET t.treatment_course_id=c.id;
-- Historic names and allocation values intentionally remain NULL when no original evidence exists.
CREATE TABLE spa_service_refunds (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, ticket_id BIGINT NULL,
 appointment_item_id BIGINT NULL, amount DECIMAL(19,2) NOT NULL, reference VARCHAR(100) NOT NULL UNIQUE,
 reason VARCHAR(250) NOT NULL, actor_user_id BIGINT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_spa_refund_order FOREIGN KEY(order_id) REFERENCES orders(id),
 CONSTRAINT fk_spa_refund_ticket FOREIGN KEY(ticket_id) REFERENCES user_service_tickets(id),
 CONSTRAINT fk_spa_refund_actor FOREIGN KEY(actor_user_id) REFERENCES users(id),
 INDEX idx_spa_refund_order(order_id), UNIQUE KEY uk_spa_refund_ticket(ticket_id)
);
ALTER TABLE orders ADD COLUMN spa_benefit_type VARCHAR(30) NULL;
UPDATE orders SET spa_benefit_type='SERVICE_PACKAGE' WHERE service_package_id IS NOT NULL;
UPDATE orders o SET o.spa_benefit_type='TREATMENT_COURSE'
 WHERE EXISTS (SELECT 1 FROM treatment_purchases p WHERE p.order_id=o.id)
 OR EXISTS (SELECT 1 FROM treatment_changes c WHERE c.settlement_order_id=o.id);
