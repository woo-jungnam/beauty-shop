CREATE TABLE banners (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    badge VARCHAR(100) NULL,
    description VARCHAR(500) NULL,
    image_url VARCHAR(1000) NOT NULL,
    target_url VARCHAR(500) NULL,
    cta_text VARCHAR(50) NULL,
    position VARCHAR(50) NOT NULL DEFAULT 'HERO_SLIDE',
    sort_order INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by VARCHAR(100) NULL,
    updated_by VARCHAR(100) NULL,
    INDEX idx_banners_position_active (position, is_active, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed default hero slides and side banners
INSERT INTO banners (title, badge, description, image_url, target_url, cta_text, position, sort_order, is_active, created_by)
VALUES
('Mỹ Phẩm Chính Hãng & Clinic Chuẩn Y Khoa.', 'HASAKI CHÍNH HÃNG 100%', 'Cam kết 100% hàng thật, date xa chuẩn FEFO, đền bù 200% nếu phát hiện hàng giả. Dịch vụ khám da miễn phí với bác sĩ chuyên khoa.', 'https://images.unsplash.com/photo-1522337360788-8b13dee7a37e?auto=format&fit=crop&w=1000&q=80', 'products', 'Mua Sắm Ngay', 'HERO_SLIDE', 1, TRUE, 'system'),
('Đại Tiệc Flash Sale - Giảm Giá Đến 50% Hôm Nay.', 'HASAKI DEALS GIỜ VÀNG', 'Cơ hội sở hữu tinh chất phục hồi da B5, kem chống nắng kiềm dầu quang phổ rộng và kem dưỡng ẩm từ các thương hiệu hàng đầu thế giới.', 'https://images.unsplash.com/photo-1620916566398-39f1143ab7be?auto=format&fit=crop&w=1000&q=80', '#flash-sale', 'Săn Deal Ngay', 'HERO_SLIDE', 2, TRUE, 'system'),
('Dịch Vụ Điều Trị & Chăm Sóc Da Chuẩn Y Khoa.', 'HASAKI CLINIC & SPA', 'Quy trình Aqua Peel làm sạch sâu đa tầng, điện di vi điểm căng bóng và trị mụn chuẩn y khoa không thâm sẹo với công nghệ FDA tiên tiến.', 'https://images.unsplash.com/photo-1540555700478-4be289fbecef?auto=format&fit=crop&w=1000&q=80', 'booking', 'Đặt Lịch Hẹn Ngay', 'HERO_SLIDE', 3, TRUE, 'system'),
('Flash Sale Mỹ Phẩm Chính Hãng', 'DEAL GIỜ VÀNG', 'Giảm đến 50%, freeship từ 249K và quà fullsize giới hạn hôm nay.', 'https://images.unsplash.com/photo-1620916566398-39f1143ab7be?auto=format&fit=crop&w=700&q=80', '#flash-sale', 'Săn deal', 'HERO_SIDE', 1, TRUE, 'system'),
('Soi Da 3D & Điều Trị Chuẩn Y Khoa', 'SPA & CLINIC', 'Đặt lịch Aqua Peel, điện di phục hồi và tư vấn routine cùng chuyên viên.', 'https://images.unsplash.com/photo-1540555700478-4be289fbecef?auto=format&fit=crop&w=700&q=80', 'booking', 'Đặt lịch', 'HERO_SIDE', 2, TRUE, 'system');
