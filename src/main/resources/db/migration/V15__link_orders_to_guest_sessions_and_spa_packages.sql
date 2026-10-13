ALTER TABLE orders
    ADD COLUMN guest_session_id VARCHAR(255) NULL,
    ADD COLUMN service_package_id BIGINT NULL;

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_service_package
        FOREIGN KEY (service_package_id) REFERENCES service_packages(id);

CREATE INDEX idx_orders_guest_session_id ON orders (guest_session_id);
CREATE INDEX idx_orders_service_package_id ON orders (service_package_id);
