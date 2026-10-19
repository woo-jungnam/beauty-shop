ALTER TABLE facilities ADD COLUMN resource_type VARCHAR(40) NOT NULL DEFAULT 'GENERAL',
    ADD COLUMN capacity INT NOT NULL DEFAULT 1,
    ADD CONSTRAINT chk_facility_capacity CHECK (capacity > 0);

CREATE TABLE service_facility_requirements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_id BIGINT NOT NULL,
    resource_type VARCHAR(40) NOT NULL,
    units INT NOT NULL,
    created_by VARCHAR(100), updated_by VARCHAR(100),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_service_resource_type UNIQUE (service_id, resource_type),
    CONSTRAINT fk_service_resource_service FOREIGN KEY (service_id) REFERENCES beauty_services(id),
    CONSTRAINT chk_service_resource_units CHECK (units > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE appointment_item_resource_requirements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    appointment_item_id BIGINT NOT NULL,
    resource_type VARCHAR(40) NOT NULL,
    units INT NOT NULL,
    created_by VARCHAR(100), updated_by VARCHAR(100),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_item_resource_type UNIQUE (appointment_item_id, resource_type),
    CONSTRAINT fk_item_resource_item FOREIGN KEY (appointment_item_id) REFERENCES appointment_items(id) ON DELETE CASCADE,
    CONSTRAINT chk_item_resource_units CHECK (units > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE facility_allocations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    appointment_item_id BIGINT NOT NULL,
    facility_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    created_by VARCHAR(100), updated_by VARCHAR(100),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_item_facility UNIQUE (appointment_item_id, facility_id),
    CONSTRAINT fk_allocation_item FOREIGN KEY (appointment_item_id) REFERENCES appointment_items(id) ON DELETE CASCADE,
    CONSTRAINT fk_allocation_facility FOREIGN KEY (facility_id) REFERENCES facilities(id),
    CONSTRAINT chk_allocation_quantity CHECK (quantity > 0),
    INDEX idx_allocation_facility (facility_id, appointment_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE facility_blocks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    facility_id BIGINT NOT NULL,
    start_at DATETIME(6) NOT NULL,
    end_at DATETIME(6) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_by VARCHAR(100), updated_by VARCHAR(100),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_facility_block_facility FOREIGN KEY (facility_id) REFERENCES facilities(id),
    CONSTRAINT chk_facility_block_interval CHECK (end_at > start_at),
    INDEX idx_facility_block_interval (facility_id, start_at, end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
