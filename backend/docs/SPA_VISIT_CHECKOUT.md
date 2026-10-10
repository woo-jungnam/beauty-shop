# Thanh toán lượt Spa

Mặc định chốt hóa đơn sau khi lịch hẹn `COMPLETED`. Mỗi mục phải có kết quả `PERFORMED` hoặc `SKIPPED`; dữ liệu cũ `LEGACY_FINALIZED` cần đối soát, không được tự suy ra đã thực hiện.

Chỉ thu giá đã lưu lúc đặt của các mục `PERFORMED` không dùng vé. Mục dùng vé và mục bỏ qua không thu thêm. Cộng các giá gốc trước, làm tròn tổng một lần về VND theo `HALF_UP`. Hóa đơn 0 VND được đánh dấu `PAID` và không tạo giao dịch thu tiền. Giá, tên và chi tiết hóa đơn được lưu cố định; một lịch hẹn có một hóa đơn.

`ADMIN`, `STAFF` và `SPA_RECEPTION` được tạo hóa đơn, xem và ghi nhận tiền mặt. Khách chỉ đọc hóa đơn của mình cùng hướng dẫn thanh toán. Các API sau yêu cầu đăng nhập:

| API | Nội dung |
| --- | --- |
| `GET /api/v1/appointments/checkout-policy` | Chính sách mặc định |
| `POST /api/v1/appointments/{id}/invoice` | Body `{"paymentMethod":"BANK","notes":"..."}` hoặc `CASH`; header `Idempotency-Key` |
| `GET /api/v1/appointments/{id}/invoice` | Giá đã chốt, thực thu, số còn thiếu, hướng dẫn thanh toán |
| `POST /api/v1/appointments/{id}/invoice/cash-receipts` | Body `{"amount":100000}`; mỗi phiếu thu có header `Idempotency-Key` riêng |

`id` là appointmentId, không phải orderId. Hai thao tác POST trả HTTP 200 kể cả tạo invoice mới; cả hai bắt buộc header `Idempotency-Key` không trắng, tối đa 128 ký tự. Khóa invoice và khóa phiếu thu có mục đích riêng; mỗi phiếu thu tiền mới dùng khóa riêng. Nếu invoice đã tồn tại, gọi lại không lập invoice thứ hai; cùng khóa với nội dung khác bị từ chối, phương thức thanh toán phải khớp invoice đã chốt. `paymentMethod` chỉ nhận BANK/CASH, không nhận COD. `amount` là VND nguyên ít nhất 1, tối đa 10 chữ số và không vượt số dư. Không gửi amount, danh sách dịch vụ hoặc customerId khi lập invoice: backend lấy dữ liệu đã thực hiện và chủ lịch đã lưu.

SPA_THERAPIST không tự có quyền xem hoặc thu invoice khách chỉ vì được giao một item. API `GET checkout-policy` dành cho mọi tài khoản đã đăng nhập; `GET invoice` dành cho chủ invoice hoặc ADMIN/STAFF/SPA_RECEPTION.

Chuyển khoản dùng webhook SePay hiện có. Có thể thu nhiều lần hoặc thu tiền mặt cho phần còn thiếu của hóa đơn BANK. QR luôn hiển thị số còn thiếu. Phiếu thu tiền mặt lưu người xác nhận trong giao dịch `gateway=CASH`; gửi lại cùng khóa và số tiền không thu thêm. Khóa cũ với số tiền khác hoặc tiền mặt lớn hơn số còn thiếu bị từ chối.

Khoản chuyển vào tài khoản VND phải là số nguyên; `100.00` hợp lệ, `100.10` được ghi `FAILED`, giữ nguyên dữ liệu nhận nhưng không cộng vào thực thu hay số tiền đã thanh toán. Nguồn ledger webhook luôn là `SEPAY`; metadata `gateway` của payload không thể giả nguồn tiền mặt.

Đơn lượt Spa có `appointmentId`, trạng thái `COMPLETED`, không vận chuyển, không giữ tồn kho, không phát vé mới và không hết hạn theo thời hạn trả tiền của đơn sản phẩm. Tổng hợp tiền thu tách BANK, COD và CASH; hoàn tiền dùng thời điểm xác nhận riêng.

Không áp dụng mặc định tiền cọc, phí hủy hoặc phí vắng mặt. Không tự hoàn tiền theo số dịch vụ bỏ qua. API xác nhận hoàn tiền hiện có, chỉ dành cho ADMIN, hỗ trợ xác nhận toàn bộ số tiền thực thu còn chưa hoàn của hóa đơn; phải có số tiền, mã xác nhận riêng và `approvalReason`. Ghi nhận người phê duyệt và lý do. Thanh toán ngân hàng đến sau khi đã hoàn được đưa về `REFUND_PENDING` để đối soát, không mở lại hóa đơn. Chính sách hoàn một phần, đổi giá đã chốt hoặc thu các loại phí cần quyết định nghiệp vụ riêng.

Migration `V32__spa_visit_checkout.sql` thêm liên kết duy nhất lịch hẹn–hóa đơn, bảng chi tiết dịch vụ riêng và vai trò `ROLE_SPA_RECEPTION`; không đổi các bản migration đã áp dụng.
