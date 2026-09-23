-- ====================================================================
-- MIGRATION V10: DERMATOLOGY & INGREDIENT SUBSYSTEM
-- Chuyển đổi mô hình dữ liệu sang Dược mỹ phẩm chuyên sâu phục vụ AI Chatbot & E-Commerce
-- Target Tables: products, product_variants, product_images,
--                ingredients, product_ingredients,
--                skin_types, product_skin_compatibility,
--                skin_concerns, product_skin_concerns,
--                product_usage_details
-- ====================================================================

DROP PROCEDURE IF EXISTS migrate_v10_dermatology_subsystem;

DELIMITER //
CREATE PROCEDURE migrate_v10_dermatology_subsystem()
BEGIN
    -- -------------------------------------------------------------
    -- 1. BẢNG PRODUCTS: Bổ sung các cờ tóm tắt nhanh (Quick Flags)
    -- -------------------------------------------------------------
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS 
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products' AND COLUMN_NAME = 'has_fragrance'
    ) THEN
        ALTER TABLE products ADD COLUMN has_fragrance BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Có chứa hương liệu không';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS 
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products' AND COLUMN_NAME = 'has_alcohol'
    ) THEN
        ALTER TABLE products ADD COLUMN has_alcohol BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Có chứa cồn khô không';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS 
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products' AND COLUMN_NAME = 'key_actives_summary'
    ) THEN
        ALTER TABLE products ADD COLUMN key_actives_summary VARCHAR(500) NULL COMMENT 'Tóm tắt các hoạt chất nổi bật';
    END IF;

    -- -------------------------------------------------------------
    -- 2. BẢNG PRODUCT_VARIANTS: Tách dung tích, giá gốc & tiền tệ
    -- -------------------------------------------------------------
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS 
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_variants' AND COLUMN_NAME = 'volume_value'
    ) THEN
        ALTER TABLE product_variants ADD COLUMN volume_value DECIMAL(10, 2) NULL COMMENT 'Giá trị dung tích (số)';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS 
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_variants' AND COLUMN_NAME = 'volume_unit'
    ) THEN
        ALTER TABLE product_variants ADD COLUMN volume_unit VARCHAR(20) DEFAULT 'ml' COMMENT 'Đơn vị: ml, g, miếng';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS 
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_variants' AND COLUMN_NAME = 'original_price'
    ) THEN
        ALTER TABLE product_variants ADD COLUMN original_price DECIMAL(12, 2) NULL COMMENT 'Giá niêm yết ban đầu trước khuyến mãi';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS 
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_variants' AND COLUMN_NAME = 'currency'
    ) THEN
        ALTER TABLE product_variants ADD COLUMN currency VARCHAR(10) DEFAULT 'VND' COMMENT 'Đơn vị tiền tệ';
    END IF;

    -- -------------------------------------------------------------
    -- 3. BẢNG PRODUCT_IMAGES: Bổ sung loại ảnh (image_type)
    -- -------------------------------------------------------------
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS 
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_images' AND COLUMN_NAME = 'image_type'
    ) THEN
        ALTER TABLE product_images ADD COLUMN image_type VARCHAR(50) DEFAULT 'PRODUCT_FRONT' COMMENT 'PRODUCT_FRONT, PRODUCT_BACK, INGREDIENT_LIST, TEXTURE, HOW_TO_USE';
    END IF;

    -- -------------------------------------------------------------
    -- 4. BẢNG INGREDIENTS: Từ điển thành phần hóa mỹ phẩm chuẩn
    -- -------------------------------------------------------------
    CREATE TABLE IF NOT EXISTS ingredients (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        name VARCHAR(255) NOT NULL,
        inci_name VARCHAR(255) NOT NULL,
        slug VARCHAR(255) NOT NULL UNIQUE,
        description TEXT,
        functions JSON NULL,
        benefits JSON NULL,
        potential_concerns JSON NULL,
        ewg_score INT DEFAULT 1,
        is_active_ingredient BOOLEAN DEFAULT FALSE,
        created_by VARCHAR(100) DEFAULT 'system',
        updated_by VARCHAR(100) DEFAULT 'system',
        is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        INDEX idx_ing_name (name),
        INDEX idx_ing_inci (inci_name)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

    -- -------------------------------------------------------------
    -- 5. BẢNG PRODUCT_INGREDIENTS: Sản phẩm - Thành phần & Nồng độ
    -- -------------------------------------------------------------
    CREATE TABLE IF NOT EXISTS product_ingredients (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        product_id BIGINT NOT NULL,
        ingredient_id BIGINT NOT NULL,
        concentration DECIMAL(5, 2) NULL,
        concentration_unit VARCHAR(10) DEFAULT '%',
        is_key_active BOOLEAN DEFAULT FALSE,
        display_order INT DEFAULT 0,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT uk_prod_ingredient UNIQUE (product_id, ingredient_id),
        CONSTRAINT fk_pi_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
        CONSTRAINT fk_pi_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients(id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

    -- -------------------------------------------------------------
    -- 6. BẢNG SKIN_TYPES: Danh mục loại da chuẩn
    -- -------------------------------------------------------------
    CREATE TABLE IF NOT EXISTS skin_types (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        code VARCHAR(50) NOT NULL UNIQUE,
        name VARCHAR(100) NOT NULL,
        description VARCHAR(255),
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

    -- -------------------------------------------------------------
    -- 7. BẢNG PRODUCT_SKIN_COMPATIBILITY: Tương thích loại da & Cảnh báo
    -- -------------------------------------------------------------
    CREATE TABLE IF NOT EXISTS product_skin_compatibility (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        product_id BIGINT NOT NULL,
        skin_type_id BIGINT NOT NULL,
        is_recommended BOOLEAN NOT NULL DEFAULT TRUE,
        score DECIMAL(3, 2) NOT NULL DEFAULT 0.80,
        contraindication_reason TEXT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT uk_prod_skintype UNIQUE (product_id, skin_type_id),
        CONSTRAINT fk_pcomp_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
        CONSTRAINT fk_pcomp_skintype FOREIGN KEY (skin_type_id) REFERENCES skin_types(id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

    -- -------------------------------------------------------------
    -- 8. BẢNG SKIN_CONCERNS: Danh mục vấn đề da liễu
    -- -------------------------------------------------------------
    CREATE TABLE IF NOT EXISTS skin_concerns (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        code VARCHAR(50) NOT NULL UNIQUE,
        name VARCHAR(150) NOT NULL,
        description VARCHAR(255),
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

    -- -------------------------------------------------------------
    -- 9. BẢNG PRODUCT_SKIN_CONCERNS: Sản phẩm - Vấn đề da & Điểm số
    -- -------------------------------------------------------------
    CREATE TABLE IF NOT EXISTS product_skin_concerns (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        product_id BIGINT NOT NULL,
        concern_id BIGINT NOT NULL,
        score DECIMAL(3, 2) NOT NULL DEFAULT 1.00,
        notes VARCHAR(255) NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT uk_prod_concern UNIQUE (product_id, concern_id),
        CONSTRAINT fk_psc_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
        CONSTRAINT fk_psc_concern FOREIGN KEY (concern_id) REFERENCES skin_concerns(id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

    -- -------------------------------------------------------------
    -- 10. BẢNG PRODUCT_USAGE_DETAILS: Quy trình sử dụng & Cảnh báo
    -- -------------------------------------------------------------
    CREATE TABLE IF NOT EXISTS product_usage_details (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        product_id BIGINT NOT NULL UNIQUE,
        when_to_use JSON NOT NULL,
        frequency VARCHAR(100) NOT NULL,
        instructions JSON NOT NULL,
        warnings JSON NOT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        CONSTRAINT fk_pud_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

    -- -------------------------------------------------------------
    -- 11. SEED DỮ LIỆU CHUẨN: SKIN_TYPES
    -- -------------------------------------------------------------
    INSERT INTO skin_types (id, code, name, description) VALUES
    (1, 'OILY', 'Da dầu', 'Tuyến bã nhờn hoạt động mạnh, da tiết nhiều dầu, dễ bít tắc lỗ chân lông và nổi mụn.'),
    (2, 'DRY', 'Da khô', 'Thiếu ẩm và thiếu lipid tự nhiên, da dễ căng rát, bong tróc và thô ráp.'),
    (3, 'COMBINATION', 'Da hỗn hợp', 'Vùng chữ T (trán, mũi, cằm) đổ dầu trong khi hai bên má khô hoặc bình thường.'),
    (4, 'SENSITIVE', 'Da nhạy cảm', 'Lớp màng bảo vệ da mỏng yếu, dễ bị ửng đỏ, ngứa rát và châm chích khi gặp hoạt chất mạnh.'),
    (5, 'NORMAL', 'Da thường', 'Tỷ lệ dầu - nước cân bằng lý tưởng, bề mặt da mịn màng, lỗ chân lông nhỏ.')
    ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);

    -- -------------------------------------------------------------
    -- 12. SEED DỮ LIỆU CHUẨN: SKIN_CONCERNS
    -- -------------------------------------------------------------
    INSERT INTO skin_concerns (id, code, name, description) VALUES
    (10, 'ACNE', 'Mụn', 'Mụn viêm sưng, mụn ẩn, mụn đầu đen và nguy cơ bùng phát mụn trên da.'),
    (11, 'CLOGGED_PORES', 'Bít tắc lỗ chân lông', 'Tế bào chết và bã nhờn tích tụ sâu trong lỗ chân lông gây sần sùi.'),
    (12, 'UNEVEN_TONE', 'Thâm sạm & Không đều màu', 'Thâm sau mụn, nám, tàn nhang, tăng sắc tố da sau viêm.'),
    (13, 'DEHYDRATION', 'Thiếu ẩm & Khô ráp', 'Da mất nước qua biểu bì, bề mặt căng rát, bong vảy li ti.'),
    (14, 'AGING', 'Lão hóa & Nếp nhăn', 'Da chảy xệ, xuất hiện nếp nhăn, suy giảm mật độ collagen và elastin.'),
    (15, 'SENSITIVITY', 'Kích ứng & Đỏ rát', 'Hàng rào bảo vệ suy yếu, da kích ứng cần phục hồi cấp tốc.')
    ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);

    -- -------------------------------------------------------------
    -- 13. SEED DỮ LIỆU CHUẨN: INGREDIENTS (TỪ ĐIỂN HOẠT CHẤT)
    -- -------------------------------------------------------------
    INSERT INTO ingredients (id, name, inci_name, slug, description, functions, benefits, potential_concerns, ewg_score, is_active_ingredient) VALUES
    (301, 'Niacinamide', 'Niacinamide', 'niacinamide',
     'Vitamin B3 đa năng giúp làm sáng da, củng cố hàng rào bảo vệ da và hỗ trợ điều tiết bã nhờn.',
     '["skin_conditioning", "brightening", "barrier_repair"]',
     '["Hỗ trợ cải thiện hàng rào bảo vệ da", "Hỗ trợ giảm tình trạng da không đều màu", "Điều tiết lượng dầu thừa"]',
     '["Có thể gây đỏ nhẹ hoặc châm chích ở nồng độ cao (>10%) với da lần đầu sử dụng"]',
     1, 1),

    (302, 'Salicylic Acid', 'Salicylic Acid', 'salicylic-acid',
     'Axit gốc Beta Hydroxy (BHA) tan trong dầu, len lỏi sâu vào lỗ chân lông để hòa tan bã nhờn và tế bào chết.',
     '["exfoliant", "acne_control", "sebum_regulating"]',
     '["Làm sạch sâu bã nhờn trong lỗ chân lông", "Hỗ trợ giảm bít tắc và gom cồi mụn", "Kháng viêm giảm sưng"]',
     '["Có thể gây khô hoặc bong tróc nhẹ; chống chỉ định dùng quá liều trên da rất khô hoặc da đang tổn thương nặng"]',
     1, 1),

    (303, 'Glycerin', 'Glycerin', 'glycerin',
     'Chất hút ẩm tự nhiên lành tính kinh điển, giữ nước trong lớp sừng giúp da duy trì độ ẩm mịn màng.',
     '["humectant", "skin_conditioning"]',
     '["Giúp hút và giữ ẩm liên tục cho da", "Giảm tình trạng khô căng sau khi rửa mặt"]',
     '[]',
     1, 0),

    (304, 'Hyaluronic Acid', 'Sodium Hyaluronate', 'hyaluronic-acid',
     'Phân tử siêu giữ nước có khả năng ngậm lượng nước gấp 1000 lần trọng lượng của chính nó.',
     '["humectant", "plumping", "skin_conditioning"]',
     '["Cấp ẩm sâu đa tầng cho tế bào da", "Giúp bề mặt da căng mọng, mờ nếp nhăn mất nước"]',
     '[]',
     1, 1),

    (305, 'Ceramide NP', 'Ceramide NP', 'ceramide-np',
     'Lipid thiết yếu cấu tạo nên lớp màng bảo vệ da, ngăn ngừa sự thất thoát nước qua biểu bì.',
     '["barrier_repair", "skin_conditioning", "protecting"]',
     '["Củng cố lớp màng lipid sinh học", "Bảo vệ da trước tác nhân kích ứng và vi khuẩn từ môi trường"]',
     '[]',
     1, 1),

    (306, 'Panthenol (Vitamin B5)', 'Panthenol', 'panthenol-b5',
     'Pro-vitamin B5 với khả năng làm dịu cảm giác nóng rát, đỏ ngứa và tăng tốc độ tái tạo mô da.',
     '["soothing", "wound_healing", "humectant"]',
     '["Làm dịu da cháy nắng hoặc kích ứng", "Tăng sinh tế bào phục hồi tổn thương da nhanh chóng"]',
     '[]',
     1, 1),

    (307, 'Centella Asiatica (Rau má)', 'Centella Asiatica Extract', 'centella-asiatica',
     'Chiết xuất thảo dược giàu Asiaticoside và Madecassoside có đặc tính kháng viêm, giảm sưng đỏ.',
     '["soothing", "anti_inflammatory", "antioxidant"]',
     '["Kháng viêm, làm dịu nốt mụn sưng đỏ", "Thúc đẩy quá trình lành sẹo mụn"]',
     '[]',
     1, 1),

    (308, 'Zinc PCA', 'Zinc PCA', 'zinc-pca',
     'Muối kẽm kết hợp L-PCA giúp ức chế enzym 5-alpha-reductase, kiềm dầu và hạn chế vi khuẩn gây mụn.',
     '["sebum_regulating", "anti_bacterial"]',
     '["Kiểm soát bóng nhờn trên da dầu mụn", "Hỗ trợ giảm viêm và thanh lọc lỗ chân lông"]',
     '[]',
     1, 1),

    (309, 'Retinol', 'Retinol', 'retinol',
     'Dẫn xuất Vitamin A tiêu chuẩn vàng kích thích chu trình tái tạo tế bào và tổng hợp sợi collagen.',
     '["anti_aging", "cell_turnover", "collagen_booster"]',
     '["Làm mờ nếp nhăn sâu, tăng độ đàn hồi săn chắc", "Cải thiện kết cấu bề mặt da thô sần"]',
     '["Gây châm chích, bong tróc trong 2-4 tuần đầu; tăng độ nhạy cảm với ánh nắng; chống chỉ định phụ nữ mang thai"]',
     2, 1),

    (310, 'Vitamin C (Ascorbic Acid)', 'Ascorbic Acid', 'vitamin-c',
     'Chất chống oxy hóa tự nhiên mạnh mẽ ức chế enzym tyrosinase hình thành sắc tố melanin.',
     '["antioxidant", "brightening", "collagen_booster"]',
     '["Dưỡng sáng đều màu da, mờ vết thâm mụn", "Bảo vệ tế bào da khỏi các gốc tự do do tia cực tím"]',
     '["Dễ bị oxy hóa chuyển màu vàng; có thể châm chích da nhạy cảm"]',
     1, 1)
    ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);

    -- -------------------------------------------------------------
    -- 14. BACKFILL PRODUCT_VARIANTS: volume_value, unit, original_price
    -- -------------------------------------------------------------
    UPDATE product_variants
    SET original_price = price
    WHERE original_price IS NULL;

    UPDATE product_variants
    SET currency = 'VND'
    WHERE currency IS NULL;

    -- Tách volume: ví dụ 40ml -> 40, ml
    UPDATE product_variants
    SET 
        volume_unit = CASE 
            WHEN LOWER(volume) LIKE '%ml%' THEN 'ml'
            WHEN LOWER(volume) LIKE '%g%' THEN 'g'
            WHEN LOWER(volume) LIKE '%miếng%' THEN 'miếng'
            ELSE 'ml'
        END,
        volume_value = CASE 
            WHEN volume REGEXP '^[0-9]+' THEN CAST(REGEXP_SUBSTR(volume, '[0-9]+(\\.[0-9]+)?') AS DECIMAL(10,2))
            ELSE 50.00
        END
    WHERE volume_value IS NULL;

    -- -------------------------------------------------------------
    -- 15. BACKFILL PRODUCTS: has_fragrance, has_alcohol, key_actives
    -- -------------------------------------------------------------
    UPDATE products
    SET has_fragrance = CASE 
        WHEN LOWER(ingredients) LIKE '%fragrance%' OR LOWER(ingredients) LIKE '%parfum%' THEN TRUE 
        ELSE FALSE 
    END;

    UPDATE products
    SET has_alcohol = CASE 
        WHEN LOWER(ingredients) LIKE '%alcohol denat%' OR LOWER(ingredients) LIKE '%ethanol%' THEN TRUE 
        ELSE FALSE 
    END;

    UPDATE products
    SET key_actives_summary = CASE
        WHEN LOWER(name) LIKE '%effaclar%' OR LOWER(name) LIKE '%bha%' OR LOWER(name) LIKE '%salicylic%' THEN 'Niacinamide, Salicylic Acid, Zinc PCA'
        WHEN LOWER(name) LIKE '%cicaplast%' OR LOWER(name) LIKE '%b5%' OR LOWER(name) LIKE '%centella%' THEN 'Panthenol (B5), Centella Asiatica, Madecassoside'
        WHEN LOWER(name) LIKE '%hyalu%' OR LOWER(name) LIKE '%hydrating%' OR LOWER(name) LIKE '%moisturizing%' THEN 'Hyaluronic Acid, Glycerin, Ceramide NP'
        WHEN LOWER(name) LIKE '%retinol%' THEN 'Retinol, Niacinamide, Ceramide'
        WHEN LOWER(name) LIKE '%brightening%' OR LOWER(name) LIKE '%vitamin c%' THEN 'Vitamin C, Niacinamide, Hyaluronic Acid'
        ELSE 'Glycerin, Niacinamide, Sodium Hyaluronate'
    END
    WHERE key_actives_summary IS NULL;

    -- -------------------------------------------------------------
    -- 16. BACKFILL PRODUCT_SKIN_COMPATIBILITY (Khuyên dùng & Chống chỉ định)
    -- -------------------------------------------------------------
    -- Da dầu / Mụn: Phù hợp OILY (0.95), COMBINATION (0.90); Không lý tưởng cho DRY (chống chỉ định)
    INSERT IGNORE INTO product_skin_compatibility (product_id, skin_type_id, is_recommended, score, contraindication_reason)
    SELECT p.id, 1, TRUE, 0.95, NULL
    FROM products p WHERE p.skin_type = 'OILY';

    INSERT IGNORE INTO product_skin_compatibility (product_id, skin_type_id, is_recommended, score, contraindication_reason)
    SELECT p.id, 3, TRUE, 0.90, NULL
    FROM products p WHERE p.skin_type = 'OILY';

    INSERT IGNORE INTO product_skin_compatibility (product_id, skin_type_id, is_recommended, score, contraindication_reason)
    SELECT p.id, 2, FALSE, 0.30, 'Sản phẩm có chứa hoạt chất kiềm dầu và tẩy da chết có thể làm khô da hơn nếu sử dụng trên nền da khô thiếu lipid.'
    FROM products p WHERE p.skin_type = 'OILY';

    INSERT IGNORE INTO product_skin_compatibility (product_id, skin_type_id, is_recommended, score, contraindication_reason)
    SELECT p.id, 4, FALSE, 0.40, 'Chứa hoạt chất đặc trị có thể gây châm chích nhẹ đối với làn da đang bị kích ứng hoặc quá nhạy cảm.'
    FROM products p WHERE p.skin_type = 'OILY' AND (LOWER(p.name) LIKE '%bha%' OR LOWER(p.name) LIKE '%salicylic%');

    -- Da khô: Phù hợp DRY (0.95), NORMAL (0.90)
    INSERT IGNORE INTO product_skin_compatibility (product_id, skin_type_id, is_recommended, score, contraindication_reason)
    SELECT p.id, 2, TRUE, 0.95, NULL
    FROM products p WHERE p.skin_type = 'DRY';

    INSERT IGNORE INTO product_skin_compatibility (product_id, skin_type_id, is_recommended, score, contraindication_reason)
    SELECT p.id, 5, TRUE, 0.90, NULL
    FROM products p WHERE p.skin_type = 'DRY';

    -- Da nhạy cảm: Phù hợp SENSITIVE (0.95), DRY (0.90), NORMAL (0.90)
    INSERT IGNORE INTO product_skin_compatibility (product_id, skin_type_id, is_recommended, score, contraindication_reason)
    SELECT p.id, 4, TRUE, 0.95, NULL
    FROM products p WHERE p.skin_type = 'SENSITIVE';

    INSERT IGNORE INTO product_skin_compatibility (product_id, skin_type_id, is_recommended, score, contraindication_reason)
    SELECT p.id, 2, TRUE, 0.90, NULL
    FROM products p WHERE p.skin_type = 'SENSITIVE';

    INSERT IGNORE INTO product_skin_compatibility (product_id, skin_type_id, is_recommended, score, contraindication_reason)
    SELECT p.id, 5, TRUE, 0.90, NULL
    FROM products p WHERE p.skin_type = 'SENSITIVE';

    -- Mọi loại da (ALL_SKIN hoặc COMBINATION hoặc còn lại)
    INSERT IGNORE INTO product_skin_compatibility (product_id, skin_type_id, is_recommended, score, contraindication_reason)
    SELECT p.id, st.id, TRUE, 0.85, NULL
    FROM products p
    CROSS JOIN skin_types st
    WHERE p.skin_type IN ('ALL_SKIN', 'COMBINATION', 'NORMAL') OR p.skin_type IS NULL;

    -- -------------------------------------------------------------
    -- 17. BACKFILL PRODUCT_SKIN_CONCERNS
    -- -------------------------------------------------------------
    -- Mụn & Bít tắc lỗ chân lông
    INSERT IGNORE INTO product_skin_concerns (product_id, concern_id, score, notes)
    SELECT p.id, 10, 0.95, 'Hiệu quả cao trong việc hỗ trợ giảm mụn và kháng viêm'
    FROM products p
    WHERE LOWER(p.name) LIKE '%acne%' OR LOWER(p.name) LIKE '%mụn%' OR LOWER(p.name) LIKE '%effaclar%' OR LOWER(p.name) LIKE '%bha%' OR p.skin_type = 'OILY';

    INSERT IGNORE INTO product_skin_concerns (product_id, concern_id, score, notes)
    SELECT p.id, 11, 0.90, 'Làm thông thoáng bã nhờn và se mịn lỗ chân lông'
    FROM products p
    WHERE LOWER(p.name) LIKE '%kiềm dầu%' OR LOWER(p.name) LIKE '%pore%' OR LOWER(p.name) LIKE '%lỗ chân lông%' OR LOWER(p.name) LIKE '%bha%' OR p.skin_type = 'OILY';

    -- Thâm sạm & Da không đều màu
    INSERT IGNORE INTO product_skin_concerns (product_id, concern_id, score, notes)
    SELECT p.id, 12, 0.85, 'Hỗ trợ mờ thâm mụn và dưỡng sáng da'
    FROM products p
    WHERE LOWER(p.name) LIKE '%bright%' OR LOWER(p.name) LIKE '%niacinamide%' OR LOWER(p.name) LIKE '%vitamin c%' OR LOWER(p.name) LIKE '%sáng da%' OR LOWER(p.name) LIKE '%thâm%';

    -- Thiếu ẩm & Mất nước
    INSERT IGNORE INTO product_skin_concerns (product_id, concern_id, score, notes)
    SELECT p.id, 13, 0.95, 'Cung cấp độ ẩm tức thì và duy trì màng ẩm tự nhiên'
    FROM products p
    WHERE LOWER(p.name) LIKE '%hydrat%' OR LOWER(p.name) LIKE '%ẩm%' OR LOWER(p.name) LIKE '%ceramide%' OR LOWER(p.name) LIKE '%ha%' OR p.skin_type = 'DRY';

    -- Kích ứng & Phục hồi da nhạy cảm
    INSERT IGNORE INTO product_skin_concerns (product_id, concern_id, score, notes)
    SELECT p.id, 15, 0.95, 'Làm dịu mẩn đỏ và tái tạo hàng rào bảo vệ da'
    FROM products p
    WHERE LOWER(p.name) LIKE '%b5%' OR LOWER(p.name) LIKE '%cicaplast%' OR LOWER(p.name) LIKE '%cica%' OR LOWER(p.name) LIKE '%rau má%' OR LOWER(p.name) LIKE '%phục hồi%' OR p.skin_type = 'SENSITIVE';

    -- Lão hóa
    INSERT IGNORE INTO product_skin_concerns (product_id, concern_id, score, notes)
    SELECT p.id, 14, 0.90, 'Ngăn ngừa nếp nhăn và cải thiện độ đàn hồi săn chắc'
    FROM products p
    WHERE LOWER(p.name) LIKE '%retinol%' OR LOWER(p.name) LIKE '%aging%' OR LOWER(p.name) LIKE '%collagen%' OR LOWER(p.name) LIKE '%nếp nhăn%';

    -- Gán mặc định concern 13 (Cấp ẩm) cho bất kỳ sản phẩm nào chưa có concern
    INSERT IGNORE INTO product_skin_concerns (product_id, concern_id, score, notes)
    SELECT p.id, 13, 0.75, 'Chăm sóc độ ẩm cơ bản cho da'
    FROM products p
    LEFT JOIN product_skin_concerns psc ON p.id = psc.product_id
    WHERE psc.id IS NULL;

    -- -------------------------------------------------------------
    -- 18. BACKFILL PRODUCT_INGREDIENTS
    -- -------------------------------------------------------------
    -- Niacinamide (301)
    INSERT IGNORE INTO product_ingredients (product_id, ingredient_id, concentration, is_key_active, display_order)
    SELECT p.id, 301, 
           CASE WHEN LOWER(p.name) LIKE '%10%%' THEN 10.00 WHEN LOWER(p.name) LIKE '%5%%' THEN 5.00 ELSE 2.00 END,
           TRUE, 1
    FROM products p
    WHERE LOWER(p.ingredients) LIKE '%niacinamide%';

    -- Salicylic Acid (302)
    INSERT IGNORE INTO product_ingredients (product_id, ingredient_id, concentration, is_key_active, display_order)
    SELECT p.id, 302, 
           CASE WHEN LOWER(p.name) LIKE '%2%%' THEN 2.00 ELSE 0.50 END,
           TRUE, 2
    FROM products p
    WHERE LOWER(p.ingredients) LIKE '%salicylic%' OR LOWER(p.name) LIKE '%effaclar%' OR LOWER(p.name) LIKE '%bha%';

    -- Glycerin (303)
    INSERT IGNORE INTO product_ingredients (product_id, ingredient_id, concentration, is_key_active, display_order)
    SELECT p.id, 303, NULL, FALSE, 3
    FROM products p
    WHERE LOWER(p.ingredients) LIKE '%glycerin%';

    -- Hyaluronic Acid (304)
    INSERT IGNORE INTO product_ingredients (product_id, ingredient_id, concentration, is_key_active, display_order)
    SELECT p.id, 304, NULL, TRUE, 4
    FROM products p
    WHERE LOWER(p.ingredients) LIKE '%hyaluron%' OR LOWER(p.name) LIKE '%hyalu%';

    -- Ceramide NP (305)
    INSERT IGNORE INTO product_ingredients (product_id, ingredient_id, concentration, is_key_active, display_order)
    SELECT p.id, 305, NULL, TRUE, 5
    FROM products p
    WHERE LOWER(p.ingredients) LIKE '%ceramide%';

    -- Panthenol (306)
    INSERT IGNORE INTO product_ingredients (product_id, ingredient_id, concentration, is_key_active, display_order)
    SELECT p.id, 306, 
           CASE WHEN LOWER(p.name) LIKE '%b5%' THEN 5.00 ELSE NULL END,
           TRUE, 6
    FROM products p
    WHERE LOWER(p.ingredients) LIKE '%panthenol%' OR LOWER(p.name) LIKE '%b5%';

    -- Centella Asiatica (307)
    INSERT IGNORE INTO product_ingredients (product_id, ingredient_id, concentration, is_key_active, display_order)
    SELECT p.id, 307, NULL, TRUE, 7
    FROM products p
    WHERE LOWER(p.ingredients) LIKE '%centella%' OR LOWER(p.name) LIKE '%rau má%';

    -- Zinc PCA (308)
    INSERT IGNORE INTO product_ingredients (product_id, ingredient_id, concentration, is_key_active, display_order)
    SELECT p.id, 308, 1.00, TRUE, 8
    FROM products p
    WHERE LOWER(p.ingredients) LIKE '%zinc pca%' OR LOWER(p.name) LIKE '%effaclar%';

    -- -------------------------------------------------------------
    -- 19. BACKFILL PRODUCT_USAGE_DETAILS
    -- -------------------------------------------------------------
    INSERT INTO product_usage_details (product_id, when_to_use, frequency, instructions, warnings)
    SELECT 
        p.id,
        CASE 
            WHEN LOWER(p.name) LIKE '%kem chống nắng%' OR LOWER(p.name) LIKE '%sunscreen%' THEN JSON_ARRAY('Morning')
            WHEN LOWER(p.name) LIKE '%retinol%' OR LOWER(p.name) LIKE '%tẩy trang%' THEN JSON_ARRAY('Night')
            ELSE JSON_ARRAY('Morning', 'Night')
        END,
        '1-2 lần mỗi ngày',
        JSON_ARRAY(
            'Làm sạch da mặt bằng nước tẩy trang và sữa rửa mặt dịu nhẹ',
            'Sử dụng nước cân bằng (toner) để cân bằng lại độ pH tự nhiên cho da',
            'Lấy một lượng sản phẩm vừa đủ thoa đều và massage nhẹ nhàng khắp mặt và cổ',
            'Vỗ nhẹ để dưỡng chất thẩm thấu hoàn toàn trước khi thực hiện bước dưỡng kế tiếp'
        ),
        JSON_ARRAY(
            'Tránh để sản phẩm tiếp xúc trực tiếp vào mắt; nếu dính vào mắt hãy rửa ngay bằng nước sạch',
            'Ngừng sử dụng và tham khảo ý kiến bác sĩ da liễu nếu xuất hiện các dấu hiệu kích ứng nghiêm trọng',
            'Nên thử nghiệm trên một vùng da nhỏ ở cổ tay trước khi sử dụng thường xuyên trên toàn khuôn mặt',
            'Bắt buộc sử dụng kem chống nắng có chỉ số SPF tối thiểu 30 vào ban ngày'
        )
    FROM products p
    ON DUPLICATE KEY UPDATE frequency = VALUES(frequency);

END //
DELIMITER ;

CALL migrate_v10_dermatology_subsystem();
DROP PROCEDURE IF EXISTS migrate_v10_dermatology_subsystem;
