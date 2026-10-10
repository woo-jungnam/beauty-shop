# Chạy backend, frontend và chatbot bằng Docker

Tại thư mục gốc dự án:

```powershell
docker compose up -d --build --wait --wait-timeout 300
docker compose ps
```

Frontend: http://localhost. Backend trực tiếp: http://localhost:8080.
Swagger: http://localhost/swagger-ui/index.html.
Chatbot Docker trực tiếp: http://127.0.0.1:18000/health.
Prometheus: http://localhost:9090. Grafana: http://localhost:3000.

Luồng kết nối: trình duyệt → Nginx → frontend tĩnh hoặc `/api/` backend;
backend → `http://chatbot:8000`; chatbot → MySQL cùng dự án và API Gemini.
Chatbot Docker dùng cổng host 18000 (đổi bằng `CHATBOT_PORT` trong `.env`) để có
thể chạy cùng các dịch vụ phát triển đang dùng cổng 8000/8001.
Compose tự khởi động lại Nginx khi cập nhật backend hoặc frontend để dùng đúng
địa chỉ container mới.
Frontend phát triển vẫn có thể chạy `npm run dev` tại `frontend` và dùng proxy Vite.

## Cấu hình

Giữ `.env` tại gốc và `chatbot/product-agent/.env` trên máy; các file này không
được đưa vào image. `.env` cần mật khẩu MySQL đang dùng và `JWT_SECRET` riêng
tối thiểu 32 byte. Sinh khóa cho một môi trường mới:

```powershell
python -c "import secrets; print(secrets.token_urlsafe(48))"
```

Điền kết quả vào `JWT_SECRET` trong `.env`. Thay khóa làm access token cũ hết
hiệu lực; cần đăng nhập lại. Không thay mật khẩu database hiện có chỉ bằng cách
sửa `.env`, vì MySQL lưu tài khoản trong volume.

`chatbot/product-agent/.env` cần `LLM_API_KEY`, `LLM_BASE_URL`, `LLM_MODEL`,
`LLM_FALLBACK_MODEL`, `EMBEDDING_MODEL_NAME`. Compose đặt lại địa chỉ MySQL,
chọn Gemini embeddings và lưu SQLite, Qdrant, cache embeddings trong volume
`chatbot_data`. Khi khởi động, chatbot nạp sản phẩm và dịch vụ từ database thật.
Compose chờ backend hoàn tất migration trước khi khởi động chatbot.

## Kiểm tra luồng thật

```powershell
Invoke-RestMethod http://localhost/actuator/health
Invoke-RestMethod http://localhost/api/v1/chatbot/health
python chatbot/product-agent/evaluation/smoke_api.py --base-url http://127.0.0.1:18000
python chatbot/product-agent/evaluation/smoke_api.py --base-url http://localhost/api/v1/chatbot --proxy
```

Health backend phải có `status=UP`. Health chatbot qua backend phải kiểm tra
`data.status`, vì HTTP 200 cũng có thể chứa `DOWN`.
Smoke chatbot kiểm tra dữ liệu MySQL thật, phản hồi LLM và SSE đến sự kiện hoàn tất.

Kiểm tra trình duyệt với Playwright:

```powershell
Set-Location frontend
$env:E2E_BASE_URL = 'http://localhost'
npm run test:e2e -- e2e/docker-stack.spec.js --retries=0
```

Test dùng tài khoản mẫu `customer` của migration V7; có thể ghi đè bằng
`E2E_USERNAME` và `E2E_PASSWORD`. Test đăng nhập, xem danh mục/chi tiết, chat và
thêm gợi ý vào giỏ; dọn phiên kiểm thử và giỏ khách sau khi chạy.

```powershell
docker compose logs --tail 100 backend chatbot nginx
docker compose stop
```

Không dùng `docker compose down -v` khi muốn giữ dữ liệu. MySQL, Redis, Kafka,
uploads, dữ liệu chatbot và monitoring dùng named volumes.

## Khôi phục môi trường ngày 03/10/2026

MySQL đã được sao lưu tại `.local/backups/beautyshop-before-rebuild-*.sql`
trước khi đối soát migration và nâng schema. Chi tiết khôi phục, dữ liệu cũ còn
cần đối soát và các kiểm tra nằm trong
[`backend/ops/RECOVERY_2026-10-03.md`](backend/ops/RECOVERY_2026-10-03.md).
Không tự động sửa checksum hoặc chạy lại migration demo từng xóa dữ liệu.

Kết quả kiểm tra bản khôi phục: 64 test backend, 8 test chatbot, 4 test hồi quy
trình duyệt và 3 test trình duyệt dùng Docker thật đều đạt. Chat thường và SSE
cũng đạt qua cổng chatbot trực tiếp và qua Nginx → backend → chatbot.
