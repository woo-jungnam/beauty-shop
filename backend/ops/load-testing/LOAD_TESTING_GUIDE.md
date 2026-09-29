# Hướng Dẫn Kiểm Thử Tải (Load Testing) & Tối Ưu Hóa HikariCP

Tài liệu này hướng dẫn chi tiết cách thực hiện kiểm thử tải (Stress / Load Testing) backend BeautyShop bằng công cụ **k6**, cách quan sát các chỉ số hiệu năng (Metrics) và cách điều chỉnh cấu hình **HikariCP Connection Pool** khi mở rộng quy mô.

---

## 1. Cài đặt k6

### Trên Windows (qua Winget hoặc Choco):
```powershell
winget install k6 --source winget
# hoặc nếu dùng Chocolatey:
choco install k6
```

### Trên macOS:
```bash
brew install k6
```

### Trên Linux (Ubuntu/Debian):
```bash
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update
sudo apt-get install k6
```

---

## 2. Kịch bản Kiểm thử Tải

Hệ thống cung cấp sẵn 2 kịch bản k6 trong thư mục `ops/load-testing/`:

### Kịch bản 1: Đọc & Tra cứu Sản phẩm (`k6-catalog-browse.js`)
* **Mục tiêu**: Kiểm tra năng lực xử lý luồng đọc (Read-heavy), đo lường hiệu quả của **Redis Cache** và khả năng scale của các API công khai.
* **Tải giả lập**: Tăng dần từ 20 $\to$ 50 $\to$ 100 Virtual Users (VUs) trong 3 phút.
* **Cách chạy**:
  ```powershell
  # Chạy trực tiếp tới backend
  k6 run ops/load-testing/k6-catalog-browse.js

  # Hoặc kiểm thử qua Nginx Reverse Proxy (cổng 80):
  k6 run -e BASE_URL=http://localhost/api/v1 ops/load-testing/k6-catalog-browse.js
  ```

### Kịch bản 2: Tranh chấp Ghi & Khóa dữ liệu (`k6-checkout-stress.js`)
* **Mục tiêu**: Mô phỏng hàng chục người dùng cùng thêm giỏ và checkout sản phẩm đồng thời (Write-heavy). Đo lường:
  * Tranh chấp khóa bi quan (`PESSIMISTIC_WRITE`) trên bản ghi kho và giỏ hàng.
  * Nguy cơ cạn kiệt connection pool của HikariCP (`hikaricp.connections.pending`).
  * Khả năng xử lý tính bất biến với header `Idempotency-Key`.
* **Cách chạy**:
  ```powershell
  k6 run ops/load-testing/k6-checkout-stress.js
  ```

---

## 3. Theo dõi & Đọc Chỉ số Giám sát (Metrics)

Trong quá trình chạy k6, mở một tab terminal khác hoặc trình duyệt để quan sát các thông số sau:

### 3.1. Các chỉ số HikariCP qua Prometheus Actuator
Truy cập endpoint: `http://localhost:8080/actuator/prometheus`

Các chỉ số quan trọng cần tìm kiếm (`grep` hoặc xem trên Grafana):
1. `hikaricp_connections_active`: Số lượng kết nối đang bận thực thi query. Nếu chỉ số này liên tục chạm ngưỡng `maximum-pool-size` (mặc định 15), pool đang bị đầy.
2. `hikaricp_connections_pending`: **Chỉ số cảnh báo quan trọng nhất!** Đây là số lượng request/thread đang phải xếp hàng chờ xin connection từ HikariCP. Nếu chỉ số này $> 0$ và tăng dần, các request sắp bị timeout (10s).
3. `hikaricp_connections_idle`: Số lượng connection rảnh rỗi trong pool.
4. `hikaricp_connections_timeout_total`: Tổng số request bị lỗi `SQLTransientConnectionException` do không xin được kết nối sau 10 giây.

### 3.2. Đánh giá Kết quả k6
Sau khi bài test kết thúc, k6 sẽ in ra bảng tóm tắt:
* **`http_req_duration`**:
  * `avg` & `med`: Thời gian phản hồi trung bình và trung vị.
  * `p(90)`, `p(95)`, `p(99)`: Thời gian phản hồi của 90%, 95% và 99% request nhanh nhất.
* **`http_req_failed`**: Tỷ lệ request thất bại. Mục tiêu là $< 1\%$ với kịch bản đọc và $< 5\%$ với kịch bản tranh chấp tồn kho cao.

---

## 4. Hướng dẫn Tinh chỉnh (Tuning) khi Mở rộng Quy mô

### 4.1. Khi nào cần tăng HikariCP Pool Size?
Nếu kiểm thử cho thấy `hikaricp_connections_pending` thường xuyên $> 0$ và CPU của máy chủ MySQL vẫn còn thấp ($< 60\%$):
* Tăng `HIKARI_MAX_POOL_SIZE` trong `.env` hoặc `application.yaml`:
  ```yaml
  # Mặc định là 15 -> Tăng lên 30 hoặc 50 tùy theo RAM và CPU của máy chủ DB
  HIKARI_MAX_POOL_SIZE: 35
  HIKARI_MIN_IDLE: 10
  ```
* **Lưu ý quan trọng**: Đồng thời phải đảm bảo cấu hình `max_connections` trong MySQL lớn hơn tổng số kết nối của tất cả replica backend cộng lại. Ví dụ: Nếu chạy 3 replica backend với pool size 35 $\implies 3 \times 35 = 105$ connections, do đó MySQL `max_connections` cần đặt tối thiểu 150 - 200 (đã cấu hình sẵn 200 trong `docker-compose.yml`).

### 4.2. Tối ưu hóa Thời gian Giữ Lock (Hold Lock Duration)
* Đảm bảo không thực hiện các tác vụ tính toán mã băm SHA-256 nặng hoặc gọi HTTP ra bên ngoài (như Chatbot AI) bên trong transaction `@Transactional`.
* Các câu truy vấn khóa bi quan (`SELECT ... FOR UPDATE`) đã được sắp xếp tăng dần theo ID ([StockAllocationService](file:///d:/khoaluantotnghiep/backend/src/main/java/com/core/beautyshop/modules/inventory/application/service/StockAllocationService.java) và [AppointmentServiceImpl](file:///d:/khoaluantotnghiep/backend/src/main/java/com/core/beautyshop/modules/spa/application/service/impl/AppointmentServiceImpl.java)), đảm bảo không xảy ra Deadlock dù hàng trăm user cùng tranh chấp.
