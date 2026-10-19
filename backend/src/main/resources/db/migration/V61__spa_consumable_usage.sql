CREATE TABLE spa_service_materials (
 service_id BIGINT NOT NULL, variant_id BIGINT NOT NULL, quantity INTEGER NOT NULL,
 PRIMARY KEY(service_id,variant_id),
 CONSTRAINT fk_spa_material_service FOREIGN KEY(service_id) REFERENCES beauty_services(id),
 CONSTRAINT fk_spa_material_variant FOREIGN KEY(variant_id) REFERENCES product_variants(id)
);
CREATE TABLE spa_appointment_material_plans (
 appointment_item_id BIGINT NOT NULL, variant_id BIGINT NOT NULL, quantity INTEGER NOT NULL,
 PRIMARY KEY(appointment_item_id,variant_id),
 CONSTRAINT fk_spa_material_plan_item FOREIGN KEY(appointment_item_id) REFERENCES appointment_items(id),
 CONSTRAINT fk_spa_material_plan_variant FOREIGN KEY(variant_id) REFERENCES product_variants(id)
);
CREATE TABLE spa_material_usages (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, appointment_id BIGINT NOT NULL, appointment_item_id BIGINT NOT NULL,
 reference VARCHAR(128) NOT NULL UNIQUE, variant_id BIGINT NOT NULL, warehouse_id BIGINT NOT NULL,
 quantity INTEGER NOT NULL, total_cost DECIMAL(14,2) NOT NULL, allocation_json TEXT NOT NULL,
 reason VARCHAR(500) NOT NULL, actor_id BIGINT NOT NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 INDEX idx_spa_material_usage_appointment(appointment_id,appointment_item_id),
 CONSTRAINT fk_spa_material_usage_appointment FOREIGN KEY(appointment_id) REFERENCES appointments(id),
 CONSTRAINT fk_spa_material_usage_item FOREIGN KEY(appointment_item_id) REFERENCES appointment_items(id),
 CONSTRAINT fk_spa_material_usage_variant FOREIGN KEY(variant_id) REFERENCES product_variants(id),
 CONSTRAINT fk_spa_material_usage_warehouse FOREIGN KEY(warehouse_id) REFERENCES warehouses(id),
 CONSTRAINT fk_spa_material_usage_actor FOREIGN KEY(actor_id) REFERENCES users(id)
);
CREATE TABLE spa_material_usage_waivers (
 appointment_item_id BIGINT PRIMARY KEY, reason VARCHAR(500) NOT NULL, actor_id BIGINT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_spa_material_waiver_item FOREIGN KEY(appointment_item_id) REFERENCES appointment_items(id),
 CONSTRAINT fk_spa_material_waiver_actor FOREIGN KEY(actor_id) REFERENCES users(id)
);
