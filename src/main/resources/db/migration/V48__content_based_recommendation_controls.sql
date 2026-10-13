ALTER TABLE user_product_interactions
    ADD COLUMN source_key VARCHAR(120) NULL,
    ADD UNIQUE INDEX uk_upi_source_key (source_key),
    ADD INDEX idx_upi_user_recent (user_id, created_at, id),
    ADD INDEX idx_upi_session_recent (session_id, created_at, id);

INSERT INTO system_configs (config_key, config_value, value_type, description, is_public, is_deleted, created_at, updated_at)
VALUES
('recommendation.weight.view','1.0','DECIMAL','Trọng số xem sản phẩm',0,0,NOW(),NOW()),
('recommendation.weight.search_click','1.5','DECIMAL','Trọng số chọn kết quả tìm kiếm',0,0,NOW(),NOW()),
('recommendation.weight.add_to_cart','3.0','DECIMAL','Trọng số thêm giỏ thành công',0,0,NOW(),NOW()),
('recommendation.weight.purchase','5.0','DECIMAL','Trọng số mua sản phẩm thành công',0,0,NOW(),NOW()),
('recommendation.history_window','100','INTEGER','Số tương tác gần nhất để xây dựng hồ sơ',0,0,NOW(),NOW()),
('recommendation.half_life_days','30.0','DECIMAL','Số ngày để trọng số giảm còn một nửa; 0 để tắt',0,0,NOW(),NOW()),
('recommendation.max_product_weight','10.0','DECIMAL','Giới hạn tổng đóng góp của một sản phẩm',0,0,NOW(),NOW()),
('recommendation.min_similarity','0.0','DECIMAL','Ngưỡng cosine để nhận ứng viên',0,0,NOW(),NOW()),
('recommendation.catalog_refresh_seconds','30','INTEGER','Chu kỳ đọc lại dữ liệu sản phẩm từ admin',0,0,NOW(),NOW()),
('recommendation.exclude_seen','true','BOOLEAN','Loại sản phẩm đã tương tác khỏi gợi ý',0,0,NOW(),NOW()),
('recommendation.in_stock_only','true','BOOLEAN','Chỉ gợi ý sản phẩm có tồn khả dụng',0,0,NOW(),NOW()),
('recommendation.field.name','1.0','DECIMAL','Trọng số đặc trưng tên sản phẩm',0,0,NOW(),NOW()),
('recommendation.field.category','2.0','DECIMAL','Trọng số đặc trưng danh mục',0,0,NOW(),NOW()),
('recommendation.field.brand','0.5','DECIMAL','Trọng số đặc trưng thương hiệu',0,0,NOW(),NOW()),
('recommendation.field.description','1.0','DECIMAL','Trọng số đặc trưng mô tả',0,0,NOW(),NOW()),
('recommendation.field.ingredients','3.0','DECIMAL','Trọng số đặc trưng thành phần',0,0,NOW(),NOW()),
('recommendation.field.skin','2.0','DECIMAL','Trọng số đặc trưng loại da',0,0,NOW(),NOW()),
('recommendation.field.concerns','2.0','DECIMAL','Trọng số đặc trưng nhu cầu da',0,0,NOW(),NOW()),
('recommendation.field.formula','0.5','DECIMAL','Trọng số đặc trưng công thức',0,0,NOW(),NOW())
ON DUPLICATE KEY UPDATE config_key=VALUES(config_key);

-- Derive each purchase once from an actual paid order, retaining its original age.
INSERT IGNORE INTO user_product_interactions (user_id, session_id, product_id, action_type, source_key, created_at)
SELECT DISTINCT o.user_id, CASE WHEN o.user_id IS NULL THEN o.guest_session_id ELSE NULL END,
       v.product_id, 'PURCHASE', CONCAT('order:',o.id,':product:',v.product_id),
       COALESCE(o.paid_at, o.created_at)
FROM orders o
JOIN order_items i ON i.order_id=o.id
JOIN product_variants v ON v.id=i.product_variant_id
WHERE o.is_deleted=0 AND i.is_deleted=0 AND o.payment_status='PAID' AND o.status NOT IN ('CANCELLED','RETURNED')
  AND (o.user_id IS NOT NULL OR (o.guest_session_id IS NOT NULL AND CHAR_LENGTH(o.guest_session_id)<=100))
  AND COALESCE(o.refunded_amount,0)<o.total_amount;
