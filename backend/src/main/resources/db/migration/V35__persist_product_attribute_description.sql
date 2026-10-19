-- Attribute API already accepts description; persist it without changing existing rows.
ALTER TABLE product_attribute_definitions ADD COLUMN description TEXT NULL;
