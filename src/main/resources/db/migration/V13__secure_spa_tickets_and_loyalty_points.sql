ALTER TABLE user_service_tickets
    ADD CONSTRAINT uk_user_service_ticket_order UNIQUE (order_id),
    ADD CONSTRAINT fk_user_service_ticket_order FOREIGN KEY (order_id) REFERENCES orders(id);

CREATE TABLE IF NOT EXISTS loyalty_point_awards (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    points INT NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_loyalty_award_order UNIQUE (order_id),
    CONSTRAINT fk_loyalty_award_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_loyalty_award_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT chk_loyalty_award_points CHECK (points > 0),
    INDEX idx_loyalty_award_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
