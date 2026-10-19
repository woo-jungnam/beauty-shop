-- A completed visit has exactly one immutable invoice, separate from physical inventory orders.
ALTER TABLE orders
    ADD COLUMN appointment_id BIGINT NULL,
    ADD CONSTRAINT uq_orders_appointment UNIQUE (appointment_id),
    ADD CONSTRAINT fk_orders_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(id);

CREATE TABLE spa_visit_invoice_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    appointment_item_id BIGINT NOT NULL,
    service_id BIGINT NOT NULL,
    service_name VARCHAR(255) NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(100) NULL,
    updated_by VARCHAR(100) NULL,
    CONSTRAINT uq_visit_invoice_appointment_item UNIQUE (appointment_item_id),
    CONSTRAINT fk_visit_invoice_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_visit_invoice_item FOREIGN KEY (appointment_item_id) REFERENCES appointment_items(id),
    CONSTRAINT fk_visit_invoice_service FOREIGN KEY (service_id) REFERENCES beauty_services(id),
    CONSTRAINT chk_visit_invoice_quantity CHECK (quantity = 1),
    CONSTRAINT chk_visit_invoice_price CHECK (unit_price >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO roles (name) SELECT 'ROLE_SPA_RECEPTION'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_SPA_RECEPTION');

-- A manual Spa refund records the administrator and reason; no automatic fee/refund formula is imposed.
ALTER TABLE refund_confirmations
    ADD COLUMN confirmed_by_user_id BIGINT NULL,
    ADD COLUMN approval_reason VARCHAR(250) NULL;
