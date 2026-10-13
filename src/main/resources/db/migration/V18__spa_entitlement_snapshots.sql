CREATE TABLE spa_purchase_snapshots (
    order_id BIGINT PRIMARY KEY,
    package_id BIGINT NOT NULL,
    validity_days INT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE spa_purchase_entitlements (
    order_id BIGINT NOT NULL,
    service_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    PRIMARY KEY (order_id, service_id),
    FOREIGN KEY (order_id) REFERENCES spa_purchase_snapshots(order_id),
    CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE ticket_entitlements (
    ticket_id BIGINT NOT NULL,
    service_id BIGINT NOT NULL,
    total_sessions INT NOT NULL,
    used_sessions INT NOT NULL DEFAULT 0,
    PRIMARY KEY (ticket_id, service_id),
    FOREIGN KEY (ticket_id) REFERENCES user_service_tickets(id),
    CHECK (total_sessions > 0 AND used_sessions >= 0 AND used_sessions <= total_sessions)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Existing purchases/tickets need verified historical entitlements. Do not infer
-- rights from a package that may have changed since purchase.
