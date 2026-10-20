# Kế hoạch rà soát an toàn và tối ưu cấu hình hạ tầng BeautyShop

Ngày lập: 20/10/2026.
Phạm vi: Docker Compose, Nginx Reverse Proxy, Cloudflare Tunnel, Backend Security (Spring Security, CORS, Webhook).

---

## 1. Mục tiêu và Nguyên tắc

Bảo đảm hệ thống vận hành đúng mô hình kiến trúc chuẩn (Zero-Trust Network, Defense-in-Depth, Immutable Caching):
- **Phòng vệ chiều sâu (Defense-in-Depth)**: Không mở cổng trực tiếp của các dịch vụ dữ liệu nội bộ (MySQL, Redis, Kafka, Backend, Prometheus) ra `0.0.0.0` của VPS. Chỉ Nginx tiếp nhận lưu lượng công khai.
- **Tính toàn vẹn của nhận dạng truy cập (Real-IP Identification)**: Khôi phục chính xác IP của người dùng cuối từ Cloudflare Edge thông qua header `CF-Connecting-IP`, ngăn ngừa tê liệt Rate Limiting và chặn bypass kiểm soát truy cập.
- **Bảo vệ khóa bí mật (Secrets Protection)**: Không lưu trữ token hạ tầng (Cloudflare Tunnel token) hoặc khóa bí mật trong mã nguồn theo dõi bởi Git.
- **Xác thực nghiêm ngặt (Strict Webhook Authentication)**: Kiểm tra tính hợp lệ và không rỗng của secret key đối với Webhook thanh toán SePay trước khi xác nhận đơn hàng `PAID`.
- **Linh hoạt tên miền (Dynamic CORS Configuration)**: Cho phép cấu hình danh sách Origin được phép qua biến môi trường phục vụ môi trường staging, mobile và domain production.
- **Bảo vệ chỉ số vận hành (Actuator Hardening)**: Không mở công khai endpoint `/actuator/prometheus` cho người dùng vãng lai.

---

## 2. Danh mục công việc chi tiết

| Mã | Hạng mục | Tệp tác động | Mức độ | Mục tiêu |
| :--- | :--- | :--- | :---: | :--- |
| **SEC-01** | Đóng các cổng dịch vụ nội bộ | `docker-compose.yml` | **CRITICAL** | Chỉ Nginx bind `0.0.0.0:80`. MySQL, Redis, Kafka, Backend, Prometheus, Grafana chuyển sang `127.0.0.1`. |
| **SEC-02** | Khôi phục Real-IP Cloudflare & Sửa Security Headers | `nginx/nginx.conf` | **HIGH** | Thêm dải IP Cloudflare, gán `real_ip_header CF-Connecting-IP;`, đảm bảo security headers không bị ghi đè tại các block location. |
| **SEC-03** | Ẩn Cloudflare Tunnel Token | `run_cloudflared.sh`, `run_cloudflared.bat`, `.env.example` | **HIGH** | Đọc token từ biến `CLOUDFLARE_TUNNEL_TOKEN` trong `.env`, không lưu token cứng trong Git. |
| **SEC-04** | Vá lỗ hổng kiểm tra Key SePay Webhook | `backend/.../payment/api/PaymentController.java` | **HIGH** | Chặn trường hợp secret rỗng/blank bypass so khớp `MessageDigest.isEqual`. |
| **SEC-05** | Cấu hình CORS linh hoạt qua Environment Variable | `backend/.../shared/config/CorsConfig.java`, `application.yaml` | **MEDIUM** | Cho phép truyền thêm origin qua biến `CORS_ALLOWED_ORIGINS` và `CUSTOMER_APP_URL`. |
| **SEC-06** | Siết quyền truy cập Actuator Metrics | `backend/.../shared/config/SecurityConfig.java` | **MEDIUM** | Bỏ `permitAll()` cho `/actuator/prometheus`, chỉ cho phép `ADMIN` hoặc mạng nội bộ. |

---

## 3. Thứ tự triển khai

1. **Bước 1**: Cập nhật `docker-compose.yml` (khóa các port nội bộ).
2. **Bước 2**: Cập nhật `nginx/nginx.conf` (Real-IP Cloudflare và Security Headers đầy đủ).
3. **Bước 3**: Cập nhật `run_cloudflared.sh` và `run_cloudflared.bat` (đọc token từ biến môi trường).
4. **Bước 4**: Cập nhật `PaymentController.java` (kiểm tra key rỗng).
5. **Bước 5**: Cập nhật `CorsConfig.java` & `application.yaml` (CORS linh hoạt).
6. **Bước 6**: Cập nhật `SecurityConfig.java` (bảo vệ Actuator Prometheus).
7. **Bước 7**: Kiểm tra lại toàn bộ file, biên dịch và xác minh.
