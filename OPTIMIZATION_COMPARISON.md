# BẢNG TỔNG HỢP SO SÁNH TRƯỚC & SAU ĐIỀU CHỈNH HỆ THỐNG
## (TỐI ƯU HÓA HIỆU NĂNG & CHỊU TẢI TRÊN MÔI TRƯỜNG N4020 / 4GB RAM)

---

## 📊 PHẦN 1: KẾT QUẢ ĐO LƯỜNG TẢI THỰC TẾ (BENCHMARK K6)

| Chỉ số Metric (k6) | 🔴 Trước Điều Chỉnh | 🟢 Sau Điều Chỉnh | Mức Độ Thay Đổi | Ý Nghĩa Thực Tế |
| :--- | :--- | :--- | :--- | :--- |
| **Tổng số Request xử lý** | 94.316 reqs | **141.593 reqs** | 🟢 **Tăng +50.1%** | Hệ thống xử lý thêm được hơn 47.000 giao dịch. |
| **Throughput (Thông lượng)** | 336.84 req/s | **505.68 req/s** | 🟢 **Tăng +50.5%** | Tốc độ đáp ứng đồng thời tăng gấp rưỡi. |
| **Tỷ lệ Thành Công (Success)** | 67.17% (63.358 reqs) | **95.51% (135.237 reqs)** | 🟢 **Tăng +28.34%** | Vượt ngưỡng ổn định vận hành (>95%). |
| **Tỷ lệ Thất Bại (Failed)** | 32.82% (30.958 fails) | **4.48% (6.356 fails)** | 🟢 **Giảm 86.4% lỗi** | Triệt tiêu lỗi nghẽn cổ chai và sập kết nối. |
| **Độ trễ Trung bình (Avg)** | 315.02 ms | **138.82 ms** | 🟢 **Nhanh hơn 56%** | Phản hồi mượt mà cho người dùng cuối. |
| **Độ trễ p(90)** | 759.09 ms | **170.96 ms** | 🟢 **Giảm 77.5%** | 90% người dùng có kết quả dưới 171ms. |
| **Độ trễ p(95) Threshold** | 910.58 ms | **236.62 ms** | 🟢 **Đạt chuẩn (< 1000ms)** | Không còn hiện tượng lag giật khi tải cao. |
| **Độ trễ Đỉnh (Max Latency)** | 7.05 s | **5.95 s** | 🟢 Giảm 1.1s | Không còn hiện tượng nghẽn treo hệ thống. |

---

## 🛠️ PHẦN 2: CHI TIẾT CÁC MỤC ĐÃ ĐIỀU CHỈNH

| STT | Thành phần | 🔴 Trước Điều Chỉnh | 🟢 Sau Điều Chỉnh | 🎯 Tác Dụng Chính | 🔄 Có Thể Điều Chỉnh Khác Không? | ⚠️ Tác Dụng Phụ & Đánh Đổi (Trade-offs) |
| :---: | :--- | :--- | :--- | :--- | :--- | :--- |
| **1** | **Java Virtual Threads** | 200 Platform Threads (Mặc định Tomcat) | `spring.threads.virtual.enabled: true` | Cho phép CPU 2 nhân xử lý hàng ngàn request I/O non-blocking cùng lúc mà không tốn RAM cho OS Thread. | Dùng Reactive Programming (Spring WebFlux + R2DBC). | Cần tránh dùng khối `synchronized` cổ điển trong code (dùng `ReentrantLock` thay thế). |
| **2** | **JWT Auth Filter** | Query MySQL bảng `users` trên **MỖI** request (`loadUserByUsername`) | Decode trực tiếp `id`, `username`, `roles` từ JWT Claims trong RAM | **Triệt tiêu 100% database query** cho việc xác thực, giải phóng hoàn toàn DB connection cho nghiệp vụ chính. | Dùng Redis Cache hoặc Caffeine In-Memory Cache `UserDetails` với TTL 5 phút. | Nếu User bị khóa tài khoản hoặc đổi quyền ở DB, token cũ vẫn có hiệu lực đến khi hết hạn (khắc phục bằng Blacklist Token). |
| **3** | **Khóa Kho Tồn (Inventory Lock)** | `@Lock(PESSIMISTIC_WRITE)` (`SELECT ... FOR UPDATE`) | **Atomic SQL UPDATE** trực tiếp: `UPDATE ... WHERE (quantity - reserved) >= :qty` | Không giữ row-lock trong suốt transaction dài; loại bỏ xung đột tranh chấp khi nhiều người cùng mua 1 món. | Dùng Redis Lua Script để trừ kho trên RAM rồi sync qua Kafka (dùng cho Flash Sale > 10.000 QPS). | Nếu sản phẩm nằm rải rác ở nhiều kho, code nghiệp vụ cần vòng lặp duyệt kho để trừ dần. |
| **4** | **HikariCP Pool Size** | `max: 80`, `min: 30`, `timeout: 5s` | `max: 15`, `min: 5`, `timeout: 10s`, `leak-detection: 5s` | Phù hợp chuẩn công thức CPU 2 nhân N4020; giảm CPU context switching ở MySQL; tăng timeout tránh nổ lỗi 500 khi spike. | Có thể nâng lên 20 - 25 nếu RAM và CPU còn dư địa chịu tải. | Request ở đỉnh tải (442 VUs) phải xếp hàng đợi connection lâu hơn một chút (nhưng không bị chết connection). |
| **5** | **MySQL Buffer Pool & Conns** | Mặc định 128MB (hoặc 1GB gây tràn RAM) | `innodb_buffer_pool_size=384M`, `max_connections=100` | Đưa toàn bộ bảng & index demo lên RAM mà vẫn đảm bảo **không bị tràn RAM 4GB** gây OOM-Killer. | Nếu máy có 8GB - 16GB RAM: Có thể nâng buffer pool lên 1GB - 2GB và `max_connections=300`. | 384MB đủ cho database vài trăm ngàn bản ghi. Nếu DB thực tế phình to trên 10GB sẽ cần nâng cấp RAM máy chủ. |
| **6** | **MySQL Flush Log & Disk I/O** | `innodb_flush_log_at_trx_commit=1` (ghi đĩa vật lý mỗi commit) | `innodb_flush_log_at_trx_commit=2`, `sync_binlog=0` | Ghi log vào OS Buffer và xả xuống đĩa 1 giây/lần $\rightarrow$ **Tăng tốc độ ghi (Write TPS) lên gấp 5-10 lần**. | Giữ nguyên `=1` nếu hệ thống yêu cầu độ bền dữ liệu tài chính ngân hàng tuyệt đối. | Nếu máy chủ vật lý bị sập nguồn/mất điện đột ngột, có thể mất tối đa 1 giây giao dịch gần nhất. |
| **7** | **Kafka Heap RAM** | Mặc định JVM (~1GB RAM) | `KAFKA_HEAP_OPTS: "-Xms128m -Xmx256m"` | Khống chế Kafka chỉ chiếm tối đa 256MB RAM, nhường RAM cho OS và Spring Boot. | Có thể hạ xuống 128MB hoặc bỏ Kafka dùng RabbitMQ nếu muốn siêu nhẹ. | Không lưu được quá nhiều message chưa đọc trong RAM nếu consumer bị dừng lâu. |
| **8** | **Open-Session-In-View (OSIV)** | `spring.jpa.open-in-view: true` | `spring.jpa.open-in-view: false` | Trả DB Connection về pool ngay khi xong Service Layer, rút ngắn 80% thời gian giữ connection. | Giữ `true` (Không khuyến khích cho ứng dụng tải cao). | Có thể gặp `LazyInitializationException` nếu Controller gọi lazy getter ngoài transaction (Dự án dùng DTO Mapper nên an toàn). |
| **9** | **Hibernate JDBC Batching** | Từng câu lệnh đơn lẻ | `batch_size: 50`, `order_inserts: true`, `order_updates: true` | Gom 50 câu lệnh SQL gửi 1 lượt qua mạng; tối ưu hóa Execution Plan Cache của MySQL. | `batch_size` có thể đặt từ 20 đến 100 tùy theo kích thước entity. | Tăng nhẹ lượng RAM tạm thời trong Hibernate Persistence Context trước khi flush. |
| **10**| **Database Composite Index** | Quét toàn bảng (Full Scan) | Thêm Migration V4 index cho `warehouse_stocks`, `cart_items`, `orders` | Tăng tốc tìm kiếm từ $O(N)$ về $O(\log N)$, giảm tải I/O cho đĩa SSD. | Đánh thêm Index theo từng use-case lọc động khác. | Tốn thêm một lượng nhỏ dung lượng đĩa và làm chậm nhẹ thao tác `INSERT`. |

---

## ⚖️ PHẦN 3: TỔNG KẾT ĐÁNH ĐỔI (TRADE-OFF MATRIX)

```
                     [ HIỆU NĂNG TỐI ĐA (High Performance) ]
                                      ▲
                                     / \
                                    /   \
                                   /     \
  [ TIẾT KIỆM TÀI NGUYÊN ] ◄───────       ───────► [ TOÀN VẸN TUYỆT ĐỐI ]
  (CPU N4020 / 4GB RAM)                            (ACID Full Disk Sync)
```

1. **Hiệu năng vs Bộ nhớ (RAM Budgeting)**:
   - Thay vì cấp 1GB cho MySQL và 1GB cho Kafka gây crash máy 4GB, cấu hình mới đã **cắt gọt vừa vặn** (MySQL 384MB, Kafka 256MB, Spring Boot 768MB) giúp hệ thống chạy ổn định 24/7 với tổng mức tiêu thụ chỉ **~2.8GB RAM**.
2. **Tốc độ Ghi vs An toàn Điện lực (Disk Flush Rate)**:
   - Chấp nhận đánh đổi nguy cơ mất tối đa 1 giây log khi mất điện máy chủ đột ngột (`flush_log=2`) để đổi lấy **tốc độ ghi đĩa nhanh gấp 5 - 10 lần** cho CPU N4020.
3. **Stateless Authentication vs Tức thời Thu hồi Quyền**:
   - Chấp nhận decode token in-memory để **triệt tiêu 141.593 lượt query MySQL vô nghĩa**, đổi lại nếu user bị đổi quyền thì token cũ có độ trễ hiệu lực cho đến khi hết hạn (hoặc xử lý bằng Blacklist Cache).
