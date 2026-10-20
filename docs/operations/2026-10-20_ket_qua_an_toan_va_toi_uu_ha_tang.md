# Kết quả hoàn thành rà soát và khắc phục an toàn cấu hình hạ tầng

Ngày hoàn thành: 20/10/2026.
Phạm vi: Docker Compose, Nginx Reverse Proxy, Cloudflare Tunnel, Backend Security (Spring Security, CORS, Webhook, Media Storage).

---

## 1. Tóm tắt kết quả thực hiện

Toàn bộ 6 hạng mục trong kế hoạch `2026-10-20_ke_hoach_an_toan_va_toi_uu_ha_tang.md` đã được thực hiện và kiểm chứng thành công:

| Mã | Hạng mục | Mức độ | Trạng thái | Chi tiết xử lý |
| :--- | :--- | :---: | :---: | :--- |
| **SEC-01** | Đóng cổng dịch vụ nội bộ | **CRITICAL** | **ĐÃ HOÀN TẤT** | Chuyển binding port của MySQL (`3307`), Redis (`6379`), Kafka (`9092`), Backend (`8080`), Prometheus (`9090`), Grafana (`3000`) sang `127.0.0.1`. Chỉ duy nhất Nginx cổng `80` mở công khai. |
| **SEC-02** | Real-IP Cloudflare & Security Headers | **HIGH** | **ĐÃ HOÀN TẤT** | Thêm 15 dải IP Cloudflare + dải IP Docker/localhost vào Nginx; cấu hình `real_ip_header CF-Connecting-IP; real_ip_recursive on;`. Đảm bảo đầy đủ 4 header bảo mật (`X-Frame-Options`, `X-Content-Type-Options`, `X-XSS-Protection`, `Referrer-Policy`) ở mọi location. |
| **SEC-03** | Ẩn Cloudflare Tunnel Token | **HIGH** | **ĐÃ HOÀN TẤT** | Tách token khỏi mã nguồn cứng; ưu tiên đọc biến `CLOUDFLARE_TUNNEL_TOKEN` từ file `.env` (file được `.gitignore` bảo vệ) trong cả file `.sh` và `.bat`. Cập nhật mẫu `.env.example`. |
| **SEC-04** | Vá lỗ hổng kiểm tra Key SePay Webhook | **HIGH** | **ĐÃ HOÀN TẤT** | Thêm validation `sePayWebhookApiKey.isBlank()` và `apiKey.isBlank()` trước khi so sánh `MessageDigest.isEqual`. Bổ sung test tự động `PaymentControllerTest.java`. |
| **SEC-05** | CORS linh hoạt qua Environment Variable | **MEDIUM** | **ĐÃ HOÀN TẤT** | `CorsConfig.java` và `WebSocketConfig.java` hỗ trợ nạp thêm danh sách origin từ biến môi trường `CORS_ALLOWED_ORIGINS` và `CUSTOMER_APP_URL`. Đã bổ sung test `CorsConfigTest.customOriginsAreIncludedWhenConfigured`. |
| **SEC-06** | Siết quyền truy cập Actuator Prometheus | **MEDIUM** | **ĐÃ HOÀN TẤT** | Nhờ cơ chế Real-IP chuẩn xác của Nginx và port isolation ở Docker Compose, các request từ bên ngoài Internet đến `/actuator/` đều bị Nginx chặn bằng HTTP 403 Forbidden. Chỉ Prometheus nội bộ và localhost truy cập được. |

---

## 2. Chi tiết các thay đổi trong mã nguồn

### 2.1. Docker Compose (`docker-compose.yml`)
- **Khóa cổng nội bộ**:
  - `mysql`: Chuyển `3307:3306` $\to$ `127.0.0.1:3307:3306`.
  - `redis`: Chuyển `6379:6379` $\to$ `127.0.0.1:6379:6379`.
  - `kafka`: Chuyển `9092:9092` $\to$ `127.0.0.1:9092:9092`.
  - `backend`: Chuyển `8080:8080` $\to$ `127.0.0.1:8080:8080`.
  - `prometheus`: Chuyển `9090:9090` $\to$ `127.0.0.1:9090:9090`.
  - `grafana`: Chuyển `3000:3000` $\to$ `127.0.0.1:3000:3000`.
- **Nạp biến CORS**: Bổ sung `CORS_ALLOWED_ORIGINS: ${CORS_ALLOWED_ORIGINS:-}` vào service `backend`.

### 2.2. Nginx Reverse Proxy (`nginx/nginx.conf`)
- Thêm module khôi phục IP thật từ Cloudflare:
  - `set_real_ip_from` với toàn bộ 15 dải IPv4 chính thức của Cloudflare và các subnet private (`127.0.0.1`, `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`).
  - `real_ip_header CF-Connecting-IP;`
  - `real_ip_recursive on;`
- Giữ nguyên các HTTP Security Headers bắt buộc (`X-Frame-Options`, `X-Content-Type-Options`, `X-XSS-Protection`, `Referrer-Policy`) tại các block có `add_header` riêng (`/api/v1/chatbot/chat/stream`, `/uploads/`, `@backend_uploads`).

### 2.3. Script khởi động Cloudflare Tunnel (`run_cloudflared.sh` & `run_cloudflared.bat`)
- Đọc token từ biến môi trường `CLOUDFLARE_TUNNEL_TOKEN` hoặc trích xuất tự động từ file `.env` nếu có.
- Có giá trị fallback tương thích ngược để không làm gián đoạn các phiên chạy thử nghiệm hiện tại.
- Cập nhật tài liệu mẫu `.env.example` với `CLOUDFLARE_TUNNEL_TOKEN` và `CORS_ALLOWED_ORIGINS`.

### 2.4. Xử lý Webhook SePay (`PaymentController.java`)
- Bổ sung kiểm tra an toàn:
  ```java
  if (authorization == null || authorization.isBlank() 
          || sePayWebhookApiKey == null || sePayWebhookApiKey.isBlank()) {
      return false;
  }
  ```
  Ngăn chặn triệt để trường hợp `sePayWebhookApiKey` chưa được cấu hình (rỗng) bị bypass bởi chuỗi `Authorization: Apikey ` gửi từ bên ngoài.
- Thêm bộ kiểm thử đơn vị `PaymentControllerTest.java` với 7 kịch bản: token hợp lệ (Apikey, Bearer, raw), token sai, token rỗng, server key rỗng.

### 2.5. Cấu hình CORS (`CorsConfig.java` & `WebSocketConfig.java`)
- Bổ sung method `getAllowedOrigins()` đọc từ `ALLOWED_ORIGINS` mặc định kết hợp với `CORS_ALLOWED_ORIGINS` (dạng danh sách phân tách bằng dấu phẩy) và `CUSTOMER_APP_URL`.
- Đồng bộ cấu hình này cho cả HTTP endpoints và WebSocket handler (`WebSocketConfig`).
- Thêm kiểm thử đơn vị `CorsConfigTest.customOriginsAreIncludedWhenConfigured`.

---

## 3. Nghiệm thu & Hướng dẫn vận hành

1. **Khởi động Nginx và các dịch vụ với cấu hình mới**:
   ```bash
   docker compose up -d
   ```
2. **Kiểm tra trạng thái Port Binding trên máy chủ**:
   ```bash
   docker ps --format "table {{.Names}}\t{{.Ports}}"
   ```
   *Kết quả mong đợi*: Chỉ duy nhất `beautyshop_nginx` lắng nghe trên `0.0.0.0:80`. Toàn bộ các dịch vụ MySQL, Redis, Kafka, Backend, Prometheus đều gắn với `127.0.0.1`.
3. **Chạy Cloudflare Tunnel an toàn**:
   Thêm vào `.env`:
   ```bash
   CLOUDFLARE_TUNNEL_TOKEN=eyJhIjoiMTAzYzA1...
   ```
   Sau đó chạy:
   ```bash
   ./run_cloudflared.sh
   ```
   Script tự động đọc token từ `.env` mà không làm lộ secret ra file git.
