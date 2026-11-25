CREATE TABLE IF NOT EXISTS shipper_profiles (
    user_id BIGINT PRIMARY KEY,
    vehicle_type VARCHAR(40) NULL,
    vehicle_plate VARCHAR(30) NULL,
    service_area VARCHAR(150) NULL,
    emergency_contact_name VARCHAR(100) NULL,
    emergency_contact_phone VARCHAR(20) NULL,
    work_shift VARCHAR(60) NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_shipper_profile_user FOREIGN KEY (user_id) REFERENCES users (id)
);

