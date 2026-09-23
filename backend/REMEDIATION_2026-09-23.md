# Khắc phục các vấn đề backend

Phạm vi: sửa trên working tree hiện tại, không triển khai hoặc thay đổi database đang vận hành. Báo cáo đánh giá ban đầu được giữ lại để đối chiếu.

## Các thay đổi đã triển khai

| Nhóm | Kết quả |
|---|---|
| Checkout | Khóa giỏ khi checkout; xóa giỏ cùng transaction; hỗ trợ replay theo `Idempotency-Key` với hash phạm vi user/session và hash request. Dùng lại key với payload khác bị từ chối. Client cũ không có key vẫn được khóa giỏ nhưng không có khả năng replay response. |
| Order | Khóa order khi cập nhật/hủy; cùng trạng thái là no-op; chặn hủy đơn đã trả/giao/vận chuyển; BANK phải trả đủ trước PROCESSING/SHIPPED/DELIVERED. COD đánh dấu đã thu tiền khi admin xác nhận DELIVERED. |
| Giữ chỗ | Đơn BANK mới có `paymentDeadline` sau 30 phút, điều chỉnh qua `ORDER_PAYMENT_TIMEOUT_MINUTES`. Job hủy PENDING/CONFIRMED hết hạn; webhook kiểm tra deadline ngay cả khi job chưa chạy. COD không tự hết hạn theo mốc này. |
| Tồn kho | `stock_allocations` gắn order number, variant, lô và số lượng. Các tác động release/deduct/return kiểm tra trạng thái allocation để tránh tác động lặp. Bỏ các hàm sửa tồn cũ không có chủ sở hữu reservation. |
| Hàng trả | Trở về đúng lô gốc, vào `quarantinedQuantity`; chưa cộng vào hàng được bán. Admin kiểm tra trước khi restock hoặc loại bỏ; không restock lô hết hạn. |
| Thanh toán | Claim reference duy nhất trước khi cộng tiền; rollback khi lỗi; webhook trùng đồng thời được nhận diện sau rollback. Thiếu reference dùng provider ID ổn định, không sinh ID theo timestamp. Kiểm tra tài khoản nhận và số tiền. Mã đơn mới có 32 ký tự hex; webhook vẫn hiểu mã cũ 8 ký tự. |
| Hoàn tiền | Hủy/trả hoặc tiền đến sau hủy tạo `REFUND_PENDING`. Admin xác nhận số tiền đã hoàn bằng reference ngân hàng. Ledger `refund_confirmations` chống dùng lại reference và replay thao tác. Hệ thống không tự chuyển tiền. |
| Hội viên | Cộng điểm đồng bộ trong transaction giao hàng, lỗi thì rollback; trả hàng đảo award một lần và tính lại hạng. |
| Spa | Khóa appointment khi sửa/hủy; chặn mở lại trạng thái kết thúc; giữ lock staff/ticket có thứ tự khi booking; kiểm tra service/staff đang hoạt động và chuyên môn; kiểm tra duration không vượt giờ đóng cửa hoặc quay vòng qua ngày. |
| Quyền lợi Spa | Snapshot quyền lợi theo dịch vụ tại lúc mua gói; vé dùng quota riêng từng dịch vụ. Hủy lịch hoàn đúng quota. Khi hủy đơn gói, vé chưa dùng bị thu hồi; vé đã dùng phải đối soát trước khi hoàn toàn bộ. |
| Nhân viên Spa | Lịch không chỉ định staff là PENDING. Khi xác nhận, phải có staff hợp lệ; admin/staff có thể cung cấp `staffAssignments` theo appointment item ID. Kiểm tra lại trùng lịch dưới khóa staff. |
| Bảo mật | Không đưa rejected value vào lỗi validation; loại password/access token/refresh token khỏi `toString`. Guest cart lookup không trả giỏ của user; giỏ user không dùng guest session làm định danh. |
| Query/cache | Bỏ collection-fetch trên truy vấn phân trang order/appointment; dùng batch fetch sẵn có. Bật fail-fast nếu tái xuất hiện phân trang collection trong RAM. Batch-load user cho trang lịch hẹn; giới hạn page size 100. Cache local có giới hạn số mục và TTL. |
| Job kho | Scan variant bằng keyset theo nhóm 100 ID; xử lý từng variant trong transaction riêng thay vì một transaction đọc toàn bộ kho. |
| Outbox | Claim bằng DB lease và claim token; phục hồi lease hết hạn, không cho callback cũ đánh dấu event của claim mới. Send theo batch bất đồng bộ với deadline chung; giữ thứ tự theo aggregate; consumer có inbox dedup theo event ID bền vững. DLT có listener thực tế. |
| Chatbot | Dùng cấu hình timeout cho HTTP; giới hạn số stream, đóng stream hết thời hạn, đóng response lỗi; không trả chi tiết exception nội bộ. Nginx có cấu hình SSE và giới hạn connection/IP riêng. |
| Build/schema | Docker multi-stage tự build JAR; `.dockerignore` loại env/target/log. CI kiểm tra Nginx có host backend để resolve. Bỏ tự `Flyway.repair()`, thêm migration mới, không sửa migration cũ. |

## Contract API cần phía client/admin lưu ý

1. `POST /api/v1/orders/checkout`: gửi header `Idempotency-Key` khác nhau cho mỗi lần mua mới; khi retry cùng giao dịch, giữ nguyên key và body. Header tùy chọn để tương thích client cũ.
2. Order response bổ sung `paidAmount`, `refundedAmount`, `refundReference`, `paymentDeadline`; enum payment status có `REFUND_PENDING`.
3. `POST /api/v1/admin/orders/{id}/refund-confirmation`, quyền ADMIN:

```json
{"reference":"BANK-REFUND-UNIQUE-ID","amount":100000}
```

Chỉ gửi sau khi đã hoàn tiền bên ngoài. Amount phải bằng số tiền còn phải hoàn. Reference đã dùng không được gán cho đơn hoặc số tiền khác.

4. `POST /api/v1/admin/inventory/{stockId}/inspection`, quyền ADMIN:

```json
{"quantity":1,"restock":true}
```

`restock=false` loại số lượng đó khỏi khu chờ kiểm tra; `true` đưa lại vào tồn bán của chính lô đó nếu còn hạn.

5. `PUT /api/v1/appointments/admin/{id}/status` khi xác nhận lịch chưa gán staff:

```json
{"status":"CONFIRMED","staffAssignments":{"123":45}}
```

`123` là ID appointment item, `45` là ID staff. Ticket response bổ sung `remainingByService`; ticket status bổ sung `REVOKED`.

## Migration và dữ liệu cũ

Các migration bổ sung: V16–V21. Chạy [preflight chỉ đọc](ops/preflight_review_fixes.sql) trên bản sao database trước khi rollout.

- Dữ liệu cũ chỉ có tổng reserved quantity, không lưu đơn nào giữ lô nào. Không thể phục hồi lịch sử chính xác bằng một phép đoán FEFO. Đối soát từng đơn với chứng từ kho, nhập `stock_allocations` với số lượng khớp order item và lô thực tế. Đơn chưa giao dùng RESERVED; đơn đã giao dùng DEDUCTED. Không cộng/trừ lại tồn khi chỉ khôi phục liên kết lịch sử.
- Tổng allocation RESERVED của một lô phải khớp phần `reserved_quantity` đã xác minh; không được gán cùng lượng hàng cho hai đơn. Nếu thiếu bằng chứng, giữ đơn để đối soát. Code chủ động từ chối chuyển tồn của đơn chưa có allocation.
- Quyền lợi gói Spa cũ không được tự suy ra từ cấu hình gói hiện tại vì gói có thể đã đổi. Khôi phục `spa_purchase_snapshots`/`spa_purchase_entitlements` cho đơn chờ tiền từ chứng từ mua; khôi phục `ticket_entitlements` cho vé đang có. Used theo dịch vụ phải khớp các appointment không CANCELLED; tổng total/used phải khớp vé. Trường hợp lịch sử không khớp cần admin xử lý trước khi mở lại vé.
- V20 sẽ từ chối tạo unique constraint nếu guest session có nhiều giỏ. Đối soát/gộp nội dung trùng trước migration; không tự xóa giỏ khách.
- Deadline chỉ áp dụng đơn mới, tránh tự hủy hàng loạt đơn cũ khi nâng cấp.
- Việc bỏ auto-repair có thể làm lộ checksum migration cũ đã thay đổi. Khôi phục đúng file migration đã chạy hoặc đối soát schema/history trước khi thực hiện repair có chủ đích; không bật lại auto-repair để bỏ qua lỗi.

Không chạy tự động bất kỳ bước đối soát/sửa dữ liệu production nào trong phiên làm việc này.

## Vận hành outbox

- FAILED event chặn các event tiếp theo của cùng aggregate để bảo toàn thứ tự. Sau khi sửa nguyên nhân, admin vận hành có thể đặt FAILED về PENDING, retry_count=0 trong transaction, chỉ trên event đã xác minh. Event ID không đổi nên inbox vẫn dedup được event đã xử lý.
- PROCESSING lease hết hạn được worker mới claim. Claim token ngăn worker cũ thay trạng thái claim mới; Kafka vẫn là at-least-once, inbox cần được giữ đủ lâu cho cửa sổ replay.
- Không tuyên bố exactly-once đối với email/bên thứ ba. Notification hiện vẫn chưa có transport SMTP thật; log đã được sửa để không giả báo “đã gửi”. Cần lựa chọn provider và cấu hình credentials trước khi hoàn thiện gửi email thực tế.
- Chính sách xóa audit/payment/inbox/outbox cần thời gian lưu trữ nghiệp vụ cụ thể; chưa tự đặt lịch xóa những dữ liệu này.

## Kiểm chứng

- Các test có sẵn đã được cập nhật khi contract thay đổi: lỗi xóa giỏ/cộng điểm phải lan truyền để rollback; không còn test kỳ vọng nuốt lỗi.
- Bổ sung integration test hai thread/two transactions cho checkout, thanh toán trùng, giao/trả lặp, hủy lịch và quota vé; test expiry/late-payment/refund và rollback thiếu tồn.
- Bổ sung test DB lease cũ không được đánh dấu thành công sau khi claim mới tiếp quản; test quota theo dịch vụ và lỗi validation không echo password.
- `MySqlCheckoutIntegrityTest` chạy lại các invariant trên MySQL 8 bằng Testcontainers, Flyway migrations thật, Hibernate validate. Khi không có Docker, suite này skip rõ ràng. Máy hiện tại chưa có Docker daemon hoạt động, nên chưa xác nhận MySQL migration/locking và image runtime tại chỗ.
- Chưa chạy load test; không công bố con số RPS hoặc người dùng đồng thời. Search substring, cache invalidation rộng, hạ tầng single-node và chính sách retention vẫn là các hạng mục cần đo/quyết định trước khi mở rộng.

Kết quả xác minh: `mvnw.cmd test -B` thành công với 109 test: 103 chạy đạt, 6 test MySQL bỏ qua do Docker daemon chưa hoạt động; không có failure/error. Không còn cảnh báo `HHH90003004` trong lần chạy này. `mvnw.cmd -DskipTests package -B` đóng gói JAR thành công sau lần chạy test đầy đủ. `git diff --check` không phát hiện lỗi whitespace trong phạm vi kiểm tra.

Log được ghi tại `target/fix-tests.log`, `target/fix-package.log`; báo cáo từng suite tại `target/surefire-reports`.
