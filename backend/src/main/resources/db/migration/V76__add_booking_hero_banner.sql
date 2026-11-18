-- Make the existing booking page photo configurable in the banner manager.
INSERT INTO banners (title, image_url, target_url, position, sort_order, is_active, created_by)
SELECT 'Đặt lịch Spa', '/images/spa/spa-ritual.jpg', '#spa-booking-form', 'BOOKING_HERO', 1, TRUE, 'system'
WHERE NOT EXISTS (SELECT 1 FROM banners existing WHERE existing.position = 'BOOKING_HERO');
