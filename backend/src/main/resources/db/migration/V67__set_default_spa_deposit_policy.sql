-- Cập nhật chính sách cọc mặc định cho spa (nếu đang là 0đ)
UPDATE spa_financial_policy
SET deposit_amount = 50000,
    cancellation_fee = 20000,
    no_show_fee = 50000,
    version = version + 1
WHERE id = 1 AND deposit_amount = 0;
