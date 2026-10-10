# Báo cáo sửa lỗi backend — 02/10/2026

Phạm vi: xử lý các phát hiện F01–F31 trong [báo cáo rà soát ban đầu](BACKEND_ADMIN_REVIEW_2026-10-02.md), củng cố quản trị và rà soát sâu nghiệp vụ Spa chăm sóc da/làm đẹp không xâm lấn theo xác nhận của chủ hệ thống. Các thay đổi có sẵn trong workspace được giữ lại, gồm snapshot/image của dòng đơn hàng. Chưa triển khai hoặc chạy migration trên database vận hành.

## Các phát hiện đã xử lý trên mã

| Mã | Thay đổi và kết quả mong đợi |
| --- | --- |
| F01 | V27 không còn xóa/reset dữ liệu vận hành. Script cũ được lưu ở `ops/demo/archive` để đối chiếu. Flyway bắt buộc kiểm checksum khi migrate, chấp nhận migration mới chưa chạy và bỏ tự động repair checksum. V28 tạo SKU dự phòng theo ID để không vượt độ dài do slug. |
| F02 | Các thuộc tính giá được tính của DTO có cấu hình Jackson read-only; cache có thể deserialize đúng mà không cần setter giả. |
| F03 | Tổng hợp review dùng `ApprovedReviewSummary` có kiểu dữ liệu rõ; khóa product/review và READ_COMMITTED bảo vệ rating khi duyệt/xóa đồng thời. |
| F04 | Sửa tên/xóa custom role làm tăng token version và thu hồi phiên của các user liên quan; không giữ quyền từ JWT cũ. |
| F05 | Nhập kho khóa warehouse rồi khóa/tạo lô; chuẩn hóa batch và cộng tồn trong transaction, tránh mất cập nhật và tạo hai lô null/blank cùng khóa. |
| F06 | Khóa role ADMIN để bảo vệ admin ACTIVE cuối cùng khi nhiều người cùng đổi quyền/trạng thái. Role hệ thống không được đổi tên hoặc xóa. |
| F07 | Logout theo family/session và idempotent; logout lại phiên cũ không thu hồi phiên đăng nhập mới hoặc thiết bị khác. |
| F08 | Username mới không chứa `@`; email và username không còn tạo alias đăng nhập nhập nhằng. Login kiểm lại trạng thái/quyền dưới khóa. |
| F09 | ORDER_STAFF lấy chi tiết đơn qua use case quản trị riêng; route của khách vẫn kiểm sở hữu/session. |
| F10 | Đơn sản phẩm BANK 0 VND trở thành PAID/PROCESSING, không deadline chờ tiền, không phát sự kiện trả tiền trùng. Gói Spa vẫn cần giá ít nhất 1 VND. |
| F11 | Tổng nghĩa vụ VND được làm tròn HALF_UP trước lưu và tạo QR; QR và số phải trả thống nhất. |
| F12 | Xác nhận giao COD giữ nguyên tiền đã chuyển/tiền dư, chỉ ghi thu phần còn thiếu; có ledger COD và reference ổn định để chống thu trùng. |
| F13 | Timeout loại đơn PAID và duyệt cursor, không để một nhóm đơn lỗi làm các đơn phía sau không được xử lý. |
| F14 | Số cách ly được xử lý riêng trong chuyển kho/kiểm kê, không trừ hai lần khỏi tồn bán được. |
| F15 | Chỉ phục hồi lô đã xóa khi tồn, reservation và cách ly đều bằng 0; kho inactive/deleted bị từ chối. |
| F16 | API nội bộ inventory đọc SKU chưa công khai/inactive để có thể nhập hàng trước khi bán, vẫn từ chối SKU đã xóa. |
| F17 | PO kiểm tra SKU, kho, nhà cung cấp khi tạo/duyệt/nhận; thêm cập nhật draft và tính lại tổng. |
| F18 | Chi tiết sản phẩm công khai chỉ đọc ACTIVE; dịch vụ Spa công khai loại inactive/deleted; quản trị có luồng đọc riêng. |
| F19 | Đọc giá trị thuộc tính trong transaction, tránh truy cập LAZY ngoài session. |
| F20 | Kiểm dữ liệu thuộc tính theo kiểu đã khai báo; từ chối giá trị không hợp lệ. |
| F21 | Kiểm đủ tham chiếu brand/category/tag/ingredient/attribute; không âm thầm bỏ ID không tồn tại/đã xóa. |
| F22 | Slot, booking có staff, confirm/start và reschedule kiểm ca phủ toàn bộ dịch vụ + chuẩn bị. Không có staff đủ điều kiện thì không có slot. Khóa staff và READ_COMMITTED phối hợp với sửa/xóa ca. |
| F23 | Gói bán ra phải có item hợp lệ và dịch vụ hoạt động; khóa dịch vụ khi mua để phối hợp với ngưng/xóa. Public package loại gói chứa dịch vụ không khả dụng. |
| F24 | Sửa/xóa dịch vụ và danh mục Spa xóa cache `spa_services`, tránh public tiếp tục đọc dữ liệu cũ. |
| F25 | Preparation không âm; duration + preparation phải nằm trong 720 phút mở cửa hiện tại, tránh LocalTime quay vòng hoặc vòng lặp slot sai. |
| F26 | Validity phải dương hoặc null (vô thời hạn); issuer từ chối snapshot validity sai thay vì biến vé có hạn thành vô thời hạn. |
| F27 | Bộ lọc vé userId và status được áp dụng đồng thời; không bỏ điều kiện status khi có userId. |
| F28 | Audit bao phủ các route quản trị cũ, auth, force logout, chatbot và hành động hủy/đổi lịch/chuyển trạng thái Spa. Lỗi auth được che nội dung credential. |
| F29 | SMTP thất bại được propagate để rollback receipt và có thể retry; không ghi nhận đã gửi thành công khi thực tế thất bại. |
| F30 | Cảnh báo cận hạn đọc config; config kiểm kiểu và có thể phục hồi key đã xóa. |
| F31 | Voucher từ chối minimum/maximum âm; sửa/delete có khóa, không hạ usageLimit xuống dưới số đã dùng. |

## Các lỗi bổ sung đã sửa trong quá trình BA và hồi quy

- Lịch CONFIRMED đổi staff nhưng giữ nguyên status không còn bị return sớm bỏ qua assignments; vẫn kiểm ca/kỹ năng/xung đột.
- Đổi giờ giữ thời lượng và giá đã đặt, không tính lại duration theo catalog vừa được sửa.
- Không bắt đầu hoặc đánh dấu NO_SHOW trước thời điểm lịch; notes tổng hợp bị chặn nếu vượt giới hạn lưu trữ.
- Gia hạn/bù lượt không khôi phục vé REVOKED hoặc vé không gắn đơn trả tiền hợp lệ, kiểm/khóa dịch vụ để không tạo quyền mới trên dịch vụ đã ngưng/xóa. Bù lượt không biến vé đã hết hạn thành ACTIVE; gia hạn vé vô thời hạn bị từ chối để không rút ngắn quyền.
- Expiry vé được tính từ paidAt của đơn, không từ thời điểm cấp vé; phát lại/cấp trễ không kéo dài quyền. Legacy thiếu bằng chứng paidAt phải đối soát. Vé đã quá expiry khi cấp có status EXPIRED.
- Ngưng/xóa dịch vụ bị chặn khi còn lịch PENDING/CONFIRMED/IN_PROGRESS, vé còn quyền chưa hết hạn hoặc đơn Spa chưa cấp vé cần xử lý. Mua/đặt và ngưng dịch vụ phối hợp bằng khóa; không tự chuyển dịch vụ hoặc hoàn tiền.
- Xóa/nghỉ staff, bỏ kỹ năng hoặc thu hẹp/xóa ca bị chặn nếu làm lịch còn hiệu lực mất khả năng phục vụ.
- Kiểm xung đột dùng EXISTS dưới khóa staff thay vì thêm khóa JOIN appointment, tránh vòng chờ khóa với thao tác đổi lịch. Guard nghĩa vụ dịch vụ đọc vé và đơn chưa cấp vé trong cùng một SQL statement, không bỏ sót khi chuyển từ snapshot sang vé.
- JWT mang token bị thu hồi không còn được dùng authentication còn lưu trước đó; filter chain chỉ chạy một lần.
- Bảo vệ cây category khi sửa đồng thời và default variant khi cập nhật giá/đổi default đồng thời.
- Upload từ chối SVG và dữ liệu giả dạng raster thay vì tin MIME/extension do client cung cấp.

## Quản trị và ý nghĩa số liệu

User có tìm kiếm/lọc theo trạng thái/role, lịch sử trạng thái và API thu hồi một phiên. Order detail trả notes, lịch sử trạng thái và paidAt. PO draft có thể sửa thay vì bắt buộc tạo lại.

Summary thanh toán aggregate tại database, tách tiền khớp đơn, tiền nhận sau hủy, không tìm được đơn, không nhận diện được, COD/bank và tiền hoàn đã xác nhận. Không load toàn bộ payment ledger để cộng trong Java.

Dashboard `revenue` hiện mang nghĩa **tổng tiền thực nhận trong ledger**, bao gồm COD và tiền chưa khớp đơn; `refundedAmount` trả riêng. Không dùng tổng này làm doanh thu kế toán hay suy ra net bank. `totalOrders/openOrders/pendingPaymentOrders` theo thời điểm tạo; `paidOrders/AOV` theo paidAt. Biểu đồ thu/hoàn theo thời điểm ledger/confirmed_at, bucket ngày/tháng Việt Nam; đây chưa phải transaction_date của ngân hàng.

Khách ACTIVE chỉ tính role CUSTOMER. Tồn thấp tính tổng mỗi SKU; threshold dùng MAX(min_quantity) giữa các lô do chưa có cấu hình threshold cấp SKU. Spa tách booked/COMPLETED/NO_SHOW, chỉ tính item có staff; không chặn tỷ lệ ở 100% để che quá công suất. COMPLETED minutes vẫn là thời lượng dự kiến, chưa có actualStartedAt/actualEndedAt.

Client đang hiển thị “doanh thu” cần đối chiếu lại nhãn với nghĩa gross receipts của API; không tính thu gói lần hai khi khách sử dụng vé.

## Kiểm thử và giới hạn

Kết quả chạy cuối: **BUILD SUCCESS — 238 ca, 237 đạt, 1 bỏ qua có chủ đích, 0 failure, 0 error**, hoàn tất lúc 13:42:40 ngày 02/10/2026 (UTC+7), thời gian 5 phút 31 giây. Lệnh: `./mvnw.cmd test -B`. Bằng chứng được lưu trong `target/admin-fixes-final-test.log` và `target/surefire-reports`.

Ca bỏ qua là kiểm tra grouping SQL chỉ dành cho MySQL trong suite H2; cùng ca đó đã chạy thành công trong suite MySQL. Các ca Testcontainers **không bị bỏ qua** trong lần cuối: MySqlCheckoutIntegrityTest 14, MySqlSpaConcurrencyIntegrityTest 4, OperationalMigrationIntegrityTest 2 và AdminDashboardMySqlJdbcTest 4 — tổng 24 ca MySQL đạt.

Các nhóm kiểm thử gồm: HTTP/quyền sở hữu/session; admin cuối cùng và thu hồi JWT; category/default variant/review cạnh tranh; cache JSON; nhập kho đồng thời; quarantine; COD/0 VND/QR/timeout; PO; SMTP/config; ca/slot/reassign/reschedule/vé; audit Spa; dashboard JDBC; MySQL migration V26 → latest và cạnh tranh kho/đơn/lịch/vé trên MySQL.

Database test là H2 hoặc MySQL Testcontainers riêng. H2 dùng local cache; test JSON serializer không phải kiểm tra Redis server thật. Chưa xác nhận cấu hình và dữ liệu của môi trường vận hành, callback ngân hàng thật, SMTP thật, UI frontend/mobile hoặc mọi nhánh ngoại lệ nghiệp vụ ngoài test đã nêu.

Testcontainers được cấu hình API Docker 1.44 để tương thích Docker Engine 29 hiện có; đây là điều chỉnh môi trường kiểm thử. [Docker — Engine API](https://docs.docker.com/reference/api/engine/). MySQL test container cho phép tạo trigger của migration cũ bằng cấu hình riêng, không thay cấu hình server vận hành.

## Việc cần làm khi đưa bản sửa vào môi trường có dữ liệu

1. Đối chiếu backup và chạy script chỉ đọc [preflight_admin_fixes.sql](ops/preflight_admin_fixes.sql) trên đúng database dự kiến. Script không tự sửa dữ liệu.
2. Kiểm tra Flyway history V27/V28. Nếu bản cũ đã chạy, checksum của mã mới có thể không khớp; validate sẽ dừng. **Không tự repair để bỏ qua**. Cần xác định script thực sự đã chạy và đối soát dữ liệu bị reset; bản sửa không phục hồi được dữ liệu đã mất. Xem [hướng dẫn archive](ops/demo/archive/README.md).
3. Giải quyết các lô trùng khóa sau chuẩn hóa null/blank/trim bằng đối soát stock/allocation trước V29; migration dừng nếu có trùng, không tự cộng/xóa các lô. V30 bổ sung paidAt/image snapshot; paidAt cũ thiếu chứng cứ vẫn NULL.
4. Cấu hình JWT_SECRET riêng đủ mạnh. JWT access cũ thiếu family_id bị từ chối; người dùng cần refresh/login để nhận token mới. Kiểm phiên hiện query DB mỗi request để thu hồi ngay, cần theo dõi tải thực tế.
5. Tạo ca làm đủ cho các kỹ thuật viên trước khi mở booking. Hệ thống không tự giả định mọi nhân viên làm 08:00–20:00. Giờ mở cửa hiện vẫn 08:00–20:00 theo thiết kế cũ, chưa có lịch ngày nghỉ/chi nhánh/cấu hình giờ động.
6. Đối soát dữ liệu legacy có duration/preparation/validity/voucher sai, username chứa `@`, vé thiếu snapshot hoặc paidAt. Các API mới từ chối dữ liệu sai; không tự sửa quyền lợi khách đã mua.

## Phần nghiệp vụ chưa thể kết luận “đã đầy đủ”

[Báo cáo BA Spa chi tiết](SPA_BUSINESS_REVIEW_2026-10-02.md) có ma trận 17 hành động/vai trò và 15 tiêu chí nghiệm thu. Ưu tiên xác định khả năng quản trị phòng/giường/thiết bị và phạm vi thu tiền buổi lẻ; tiếp theo là check-in/từng item, sổ giữ/làm lượt, phân quyền kỹ thuật viên và hồ sơ/quy trình. Hủy/đổi/no-show, hạn dùng theo ngày phục vụ, hoàn gói một phần và các khoản phí vẫn cần chính sách cụ thể. Trả lời “ok” chưa xác định những con số hoặc quy tắc này; chưa tự áp dụng vào code.

Các gap quản trị ngoài Spa còn gồm: nhận PO từng phần, trả nhà cung cấp/công nợ, giao thất bại/đổi trả từng phần và quản lý vòng đời media đầy đủ. Những mục này cần thiết kế nghiệp vụ riêng, không được đánh dấu đã hoàn thành chỉ vì F01–F31 đã có bản sửa.
