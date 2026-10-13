-- =========================================================================
-- V45: CLEAN SEED PRODUCT IMAGES AND INITIALIZE PUBLIC SHIPPING & SPA POLICIES
-- =========================================================================

-- 1. Initialize Shipping and Spa Policies in system_configs
INSERT INTO system_configs (config_key, config_value, value_type, description, is_public, created_at, updated_at, is_deleted)
VALUES
('shipping.free_threshold', '249000', 'DECIMAL', 'Ngưỡng giá trị đơn hàng được miễn phí vận chuyển toàn quốc (VND)', TRUE, NOW(), NOW(), FALSE),
('shipping.default_fee', '25000', 'DECIMAL', 'Phí vận chuyển tiêu chuẩn khi đơn hàng chưa đạt ngưỡng miễn phí (VND)', TRUE, NOW(), NOW(), FALSE),
('spa.opening_time', '08:00:00', 'STRING', 'Giờ mở cửa phục vụ lịch hẹn Spa hàng ngày', TRUE, NOW(), NOW(), FALSE),
('spa.closing_time', '20:00:00', 'STRING', 'Giờ đóng cửa kết thúc ca phục vụ Spa hàng ngày', TRUE, NOW(), NOW(), FALSE)
ON DUPLICATE KEY UPDATE
    config_value = VALUES(config_value),
    description = VALUES(description),
    is_public = VALUES(is_public);

-- 2. Clean mock placeholder URLs (img.com) from products with authentic commercial product photography
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1556228720-195a672e8a03?auto=format&fit=crop&w=800&q=85' WHERE id = 1;
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1556228852-80b6e5eeff06?auto=format&fit=crop&w=800&q=85' WHERE id = 2;
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1598440947619-2c35fc9aa908?auto=format&fit=crop&w=800&q=85' WHERE id = 3;
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1608248543803-ba4f8c70ae0b?auto=format&fit=crop&w=800&q=85' WHERE id = 4;
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1584308666744-24d5c474f2ae?auto=format&fit=crop&w=800&q=85' WHERE id = 5;
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1570172619644-dfd03ed5d881?auto=format&fit=crop&w=800&q=85' WHERE id = 6;
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1552046122-03184de85e08?auto=format&fit=crop&w=800&q=85' WHERE id = 7;
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1535585209827-a15fcdbc4c2d?auto=format&fit=crop&w=800&q=85' WHERE id = 8;
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1616683693504-3ea7e9ad6fec?auto=format&fit=crop&w=800&q=85' WHERE id = 9;
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1617897903246-719242758050?auto=format&fit=crop&w=800&q=85' WHERE id = 10;
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1586495777744-4413f21062fa?auto=format&fit=crop&w=800&q=85' WHERE id = 11;
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1586495777744-4413f21062fa?auto=format&fit=crop&w=800&q=85' WHERE id = 12;
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1522337360788-8b13dee7a37e?auto=format&fit=crop&w=800&q=85' WHERE id = 13;
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1512290900672-1f55a1d798a7?auto=format&fit=crop&w=800&q=85' WHERE id = 14;
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1598440947619-2c35fc9aa908?auto=format&fit=crop&w=800&q=85' WHERE id = 15;
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1512290900672-1f55a1d798a7?auto=format&fit=crop&w=800&q=85' WHERE id = 16;
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1512290900672-1f55a1d798a7?auto=format&fit=crop&w=800&q=85' WHERE id = 17;
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1535585209827-a15fcdbc4c2d?auto=format&fit=crop&w=800&q=85' WHERE id = 18;
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1535585209827-a15fcdbc4c2d?auto=format&fit=crop&w=800&q=85' WHERE id = 19;
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1556228720-195a672e8a03?auto=format&fit=crop&w=800&q=85' WHERE id = 20;
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1522337360788-8b13dee7a37e?auto=format&fit=crop&w=800&q=85' WHERE id = 21;
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1556228720-195a672e8a03?auto=format&fit=crop&w=800&q=85' WHERE id = 22;
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1586495777744-4413f21062fa?auto=format&fit=crop&w=800&q=85' WHERE id = 23;
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1592945403244-b3fbafd7f539?auto=format&fit=crop&w=800&q=85' WHERE id = 24;
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1592945403244-b3fbafd7f539?auto=format&fit=crop&w=800&q=85' WHERE id = 25;
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1620916566398-39f1143ab7be?auto=format&fit=crop&w=800&q=85' WHERE id = 26;
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1556228720-195a672e8a03?auto=format&fit=crop&w=800&q=85' WHERE id = 27;
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1556228720-195a672e8a03?auto=format&fit=crop&w=800&q=85' WHERE id = 28;
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1620916566398-39f1143ab7be?auto=format&fit=crop&w=800&q=85' WHERE id = 29;
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1620916566398-39f1143ab7be?auto=format&fit=crop&w=800&q=85' WHERE id = 30;
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1570172619644-dfd03ed5d881?auto=format&fit=crop&w=800&q=85' WHERE id = 31;
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1608248543803-ba4f8c70ae0b?auto=format&fit=crop&w=800&q=85' WHERE id = 32;
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1586495777744-4413f21062fa?auto=format&fit=crop&w=800&q=85' WHERE id = 33;
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1586495777744-4413f21062fa?auto=format&fit=crop&w=800&q=85' WHERE id = 34;
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1512290900672-1f55a1d798a7?auto=format&fit=crop&w=800&q=85' WHERE id = 35;

-- 3. Comprehensive safety net: replace any remaining img.com URLs across the entire products table
UPDATE products
SET thumbnail_url = 'https://images.unsplash.com/photo-1556228720-195a672e8a03?auto=format&fit=crop&w=800&q=85'
WHERE thumbnail_url LIKE '%img.com%';
