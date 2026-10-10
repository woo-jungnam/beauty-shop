# Kết quả hoàn thiện backend và rà soát nghiệp vụ Spa — 02/10/2026

Phạm vi: Spa chăm sóc da và làm đẹp không xâm lấn. Đợt này tiếp tục từ [31 lỗi quản trị đã sửa](BACKEND_FIX_REPORT_2026-10-02.md), triển khai các khoảng trống chính trong luồng dịch vụ và rà soát lại theo vai trò BA. Công việc thực hiện trong mã nguồn và database kiểm thử; chưa triển khai hoặc chạy migration trên database vận hành.

## Kết quả triển khai

| Phần | Hành vi sau sửa | Mã nguồn đối chiếu |
| --- | --- | --- |
| Giữ và sử dụng lượt gói | Book tăng reserved, chưa tăng used. PERFORMED chuyển reserved sang used; hủy, hết hạn PENDING và SKIPPED trả lượt. NO_SHOW ghi FORFEIT hoặc RELEASE theo snapshot, không giả là đã làm dịch vụ | [TicketUsageService](src/main/java/com/core/beautyshop/modules/spa/application/service/TicketUsageService.java), [AppointmentServiceImpl](src/main/java/com/core/beautyshop/modules/spa/application/service/impl/AppointmentServiceImpl.java) |
| Đối soát từng lượt | Movement ghi vé, loại hành động và số dư sau từng thao tác; dịch vụ/item gắn với thao tác tương ứng. Actor được ghi cho thao tác người dùng, có thể trống với job hoặc số dư lịch sử. Gia hạn/bù lượt yêu cầu lý do và khóa gửi lại; cùng khóa nhưng khác nội dung bị từ chối | [AdminSpaTicketService](src/main/java/com/core/beautyshop/modules/spa/application/service/AdminSpaTicketService.java), [TicketSessionMovement](src/main/java/com/core/beautyshop/modules/spa/domain/TicketSessionMovement.java) |
| Tiếp đón và thực hiện | Check-in riêng, lưu người và giờ đến. Bắt đầu buổi sau check-in; mỗi item có PLANNED/IN_PROGRESS/PERFORMED/SKIPPED, giờ thực tế và người bắt đầu. Chỉ hoàn tất buổi khi mọi item đã có kết quả | [AppointmentController](src/main/java/com/core/beautyshop/modules/spa/api/AppointmentController.java), [AppointmentItem](src/main/java/com/core/beautyshop/modules/spa/domain/AppointmentItem.java) |
| Lịch sử hành động | Ghi book, xác nhận/phân công, đổi lịch, check-in, start, thực hiện/bỏ mục, hủy/no-show/hết PENDING. Người xem phải có quyền trên lịch; danh sách có phân trang | [AppointmentActionHistory](src/main/java/com/core/beautyshop/modules/spa/domain/AppointmentActionHistory.java) |
| Giường, phòng và thiết bị | Khai báo loại/capacity, bảo trì và nhiều loại tài nguyên cho một dịch vụ. Chụp yêu cầu lúc book; khóa tài nguyên khi phân bổ, tính đỉnh sử dụng đồng thời. Hủy/bỏ mục/đổi lịch giải phóng đúng chỗ | [FacilitySchedulingService](src/main/java/com/core/beautyshop/modules/spa/application/service/FacilitySchedulingService.java), [chi tiết API](SPA_RESOURCE_MANAGEMENT.md) |
| Dịch vụ kéo dài quá giờ | START kiểm cả kỹ thuật viên và tài nguyên đang thực sự IN_PROGRESS, kể cả đã qua giờ dự kiến. Không chỉ dựa vào các slot kế tiếp | `hasActiveExecutionForStaff`, `requireExecutionCapacity` |
| Quyền lễ tân/kỹ thuật viên | SPA_RECEPTION điều phối và thu tiền; SPA_THERAPIST chỉ xem lịch được giao và thực hiện item của mình. ADMIN quản lý ngoại lệ. STAFF cũ giữ quyền tương thích; khi chuyển sang role chuyên biệt phải gỡ STAFF, chỉ thêm role mới vẫn giữ quyền rộng | [SpaAccessService](src/main/java/com/core/beautyshop/modules/spa/application/service/SpaAccessService.java) |
| Hồ sơ và đồng ý quy trình | Template có phiên bản bất biến, yêu cầu được chụp vào item. Chính khách nộp và xác nhận; nhân viên không giả consent. Nếu yêu cầu xem cảnh báo, START kiểm actor và hash hiện tại của hồ sơ/câu trả lời; ADMIN override có lý do và lịch sử | [SpaPreparationService](src/main/java/com/core/beautyshop/modules/spa/application/service/SpaPreparationService.java), [luồng care/forms](SPA_CARE_WORKFLOW_2026-10-02.md) |
| Thanh toán buổi lẻ | Một invoice cố định cho buổi COMPLETED, chỉ lấy giá snapshot của item PERFORMED không dùng vé. BANK/CASH thu nhiều lần; chống gửi lại và tiền mặt vượt số dư. Hóa đơn 0 đồng được PAID; không đi qua tồn kho/giao hàng/cấp vé/timeout đơn hàng | [SpaAppointmentCheckoutService](src/main/java/com/core/beautyshop/modules/spa/application/service/SpaAppointmentCheckoutService.java), [API checkout](SPA_VISIT_CHECKOUT.md) |
| Hoàn tiền buổi lẻ | Ghi nhận ADMIN xác nhận đã hoàn thủ công toàn bộ tiền thực thu chưa hoàn, kèm lý do phê duyệt; backend không tự chuyển tiền. Không tự tính tiền hoàn một phần hoặc phí. Chuyển khoản đến sau khi hoàn được đưa vào xử lý REFUND_PENDING | [OrderRefundService](src/main/java/com/core/beautyshop/modules/order/application/service/OrderRefundService.java) |
| Báo cáo quản trị | CASH được tách khỏi BANK/COD. servedMinutes dùng giờ thực tế của item PERFORMED, giới hạn trong khoảng ngày báo cáo theo giờ Việt Nam. reservedMinutes và legacyFinalizedMinutes riêng. spa-financials là số dư hiện tại: công nợ invoice buổi lẻ; buổi COMPLETED có mục PERFORMED ngoài vé chưa invoice; buổi COMPLETED/LEGACY_FINALIZED chưa invoice cần đối soát | [AdminDashboardService](src/main/java/com/core/beautyshop/modules/dashboard/application/AdminDashboardService.java) |
| Chính sách và job | Snapshot TTL/cutoff/grace/quota no-show theo booking; điều kiện expiry theo purchase/ticket. Job giải phóng PENDING hết hạn bằng giao dịch và có trace. Thay cấu hình không áp ngược lên quyền/lịch đã chụp | [SpaBookingPolicyService](src/main/java/com/core/beautyshop/modules/spa/application/service/SpaBookingPolicyService.java) |
| CRM và chăm sóc sau buổi | Phân trang khách/ghi chú. Reminder và hướng dẫn do nhân viên viết có outbox/dedup, kiểm lại lịch trước gửi, escape HTML; mặc định tắt | [luồng chăm sóc](SPA_CARE_WORKFLOW_2026-10-02.md) |

## Các lỗi phát hiện thêm trong đợt triển khai và đã sửa

1. **Thứ tự khóa khi đổi lịch:** ticket rồi staff có thể tạo deadlock với booking staff rồi ticket. Đã thống nhất staff theo ID tăng dần trước ticket.
2. **Sổ giữ chỗ nhiều item:** ghi movement sau khi giữ toàn bộ lượt làm các dòng đều có số dư cuối. Đã reserve và ghi từng movement liên tiếp trong cùng transaction sau khi có item ID.
3. **Dữ liệu đã xóa:** chặn dùng ticket/đổi lịch đã soft-delete; lọc khỏi danh sách và phạm vi kỹ thuật viên.
4. **Lịch kế tiếp khi khách trước chưa xong:** kiểm actual IN_PROGRESS dưới mutex staff/facility trước START; bảo trì ngay không được làm mất tài nguyên đang thực hiện.
5. **Giờ JDBC và Hibernate:** đọc/bind TIME/TIMESTAMP với UTC Calendar tương ứng; reminder lọc khoảng Instant từ dữ liệu ORM để xử lý cả trường hợp chuyển UTC đi qua nửa đêm.
6. **Xác nhận hồ sơ cũ:** khách sửa form sau acknowledgment nay làm hết hiệu lực hash cũ; thay template sau booking không đổi snapshot.
7. **Nguồn tiền và VND:** webhook sử dụng nguồn SEPAY của tuyến tích hợp, không cho payload tự giả CASH/COD. Số tiền chuyển vào có phần lẻ VND được FAILED và giữ giá trị gốc để đối soát, không cộng vào thực thu.
8. **Job trong môi trường tắt tác vụ:** job PENDING tuân thủ `app.jobs.enabled` như các job vận hành khác.
9. **Ngoại lệ hủy/đổi do nhân viên:** cần lý do khi thao tác cho khách khác hoặc vượt cutoff; không chỉ có trạng thái mà thiếu dấu vết quyết định.
10. **Độ chính xác thời gian check-in:** lưu microsecond để kết quả trả lần đầu và lần gửi lại phù hợp TIMESTAMP(6).

## Trình tự hành động của một buổi

1. **Khách đặt:** `POST /api/v1/appointments/book`. Trạng thái PENDING là yêu cầu chờ xác nhận; có thể đã giữ suất gói và các tài nguyên có cấu hình.
2. **Lễ tân xác nhận:** `PUT /api/v1/appointments/admin/{id}/status` với CONFIRMED và `staffAssignments` khi cần. Mỗi dịch vụ phải đủ kỹ năng, ca, slot và tài nguyên.
3. **Khách chuẩn bị:** đọc `/api/v1/spa/appointments/{id}/preparation`, nộp form-version bắt buộc và xác nhận đúng tài khoản sở hữu. Kỹ thuật viên đọc care-summary và gửi acknowledgment theo versionHash nếu policy yêu cầu.
4. **Lễ tân tiếp đón:** `PUT /api/v1/appointments/{id}/check-in`, ghi giờ đến riêng; sau đó chuyển buổi sang IN_PROGRESS khi đủ thời điểm.
5. **Kỹ thuật viên thực hiện:** `PUT /api/v1/appointments/{id}/items/{itemId}/execution` với IN_PROGRESS, rồi PERFORMED. Item chưa làm có thể SKIPPED kèm lý do. Không chuyển thẳng PLANNED thành PERFORMED.
6. **Đóng buổi phục vụ:** chuyển appointment sang COMPLETED sau khi mọi item là PERFORMED/SKIPPED. Việc này chưa chứng minh khách đã trả tiền.
7. **Lễ tân lập hóa đơn:** `POST /api/v1/appointments/{id}/invoice`, chọn BANK/CASH và gửi header `Idempotency-Key`. Dùng vé không bị tính tiền lần hai; mục bỏ qua không nằm trong hóa đơn.
8. **Thu và đối soát:** BANK qua webhook hiện có; CASH qua `POST /api/v1/appointments/{id}/invoice/cash-receipts` với khóa ổn định cho đúng chứng từ. GET invoice thể hiện tổng tiền, đã thu và còn thiếu.
9. **Quản lý kiểm tra:** `/api/v1/admin/dashboard/spa-financials`, lịch sử appointment và `/api/v1/spa/tickets/{id}/movements`. Phân biệt khoản chưa lập hóa đơn, công nợ, giữ suất, lượt vé FORFEITED do no-show và dịch vụ đã thực hiện.
10. **Chăm sóc sau buổi:** nhân viên ghi hướng dẫn và dueAt theo API riêng. Chỉ bật gửi tự động khi đã cấu hình nội dung, email và job cần dùng.

## Những phần BA còn cần quyết định hoặc thiết kế tiếp

Đây là các khoảng trống còn lại, không được báo là đã hoàn thành. Các lựa chọn tài chính chưa được chốt nên backend giữ quy tắc tương thích và không tự đặt mức phí/công thức.

| Ưu tiên | Vấn đề còn lại | Hành động cụ thể |
| --- | --- | --- |
| P2 | Dịch vụ phải dừng khi đang làm | Thiết kế STOP_SERVICE/ABORTED hoặc PARTIALLY_PERFORMED, actual end và lý do; giải phóng người/tài nguyên. Manager quyết định quota/tiền bằng luồng riêng. Hiện không dùng PERFORMED để thay thế kết quả dừng giữa chừng |
| P2 | Thêm/đổi dịch vụ trong buổi | Luồng thay item PLANNED nguyên tử: hoàn reserved cũ, tính lại snapshot giá/thời lượng/form/resource và giữ mới. Không sửa âm thầm item đã thực hiện |
| P2 | Cọc, thu trước, phí hủy/no-show và hoàn gói đã dùng một phần | Chốt điều kiện, số tiền/cách định giá và thẩm quyền; thiết kế ledger/đề nghị duyệt theo snapshot mua. Luồng hiện tại là checkout sau phục vụ, không tự thu phí hoặc hoàn một phần |
| P2 | Ngưng bán mới nhưng vẫn phục vụ quyền cũ | Tách sellable và fulfillable. Hiện guard ngăn ngưng hoàn toàn khi còn nghĩa vụ, chưa có hai trạng thái độc lập |
| P2 | Lễ tân tiếp nhận khách mới đến trực tiếp | Thiết kế tạo/tra trùng hồ sơ khách và đặt thay với consent đúng người, idempotency khi retry. Booking hiện dùng tài khoản khách đang đăng nhập |
| P2 | Điều phối hàng đợi khi quá giờ | Đã chặn START xung đột thực tế; còn cần cảnh báo, ETA và luồng đổi người/giờ có xác nhận cho khách sau |
| P2 — lỗi nhắc lịch | Đổi lịch A→B→A có thể mất reminder | Dedup hiện dùng appointment/date/time. Nếu event A đã bị bỏ qua khi lịch ở B, quay lại A không enqueue lại. Cần revision tăng mỗi lần đổi lịch, đưa vào dedup và kiểm revision lúc gửi; regression A→B→A chỉ gửi phiên bản hiện hành |
| P2 — thiếu hành động | Rút đồng ý và thu hồi override trước START | `SpaPreparationService.submit` chỉ nhận acknowledged=true; chưa có withdrawal/revoke. Override hiện bỏ qua cả yêu cầu form lẫn cảnh báo. Cần ghi quyết định append-only, vô hiệu hóa readiness/hash và chốt phạm vi override; không coi quyền quản lý là bằng chứng khách đồng ý |
| Trước bật thông báo | SMTP chưa sẵn sàng có thể làm event bị coi là đã xử lý dù chưa gửi | `NotificationServiceImpl.sendEmail` có thể return khi mail tắt/thiếu sender; dispatch/inbox vẫn chống lặp. Kiểm cấu hình và smoke SMTP trước bật job; cần thiết kế trạng thái chưa gửi/replay có kiểm soát. Recheck lịch chỉ phản ánh thời điểm consumer kiểm tra, không khóa xuyên lần gửi SMTP |
| Trước vận hành | Giao diện và cấu hình cơ sở | Nối frontend/mobile với các action mới; nhập giường/máy/yêu cầu dịch vụ, form/role/policy. Backend không tự suy các thông số thực tế của Spa |
| Trước nâng cấp | Lịch/quyền lợi cũ | Đối chiếu dữ liệu và tài nguyên của lịch đang mở; không suy sự thực hiện/đồng ý từ status cũ. Các COMPLETED cũ được đánh dấu LEGACY_FINALIZED, không tự lập hóa đơn hay ghi phút thực tế |

Chi tiết nhận định và điều kiện nghiệm thu nằm trong [báo cáo BA cập nhật](SPA_BUSINESS_REVIEW_2026-10-02.md).

## Mặc định cấu hình và tác động tương thích

| Khóa | Mặc định hiện tại | Ý nghĩa |
| --- | --- | --- |
| spa.booking.pending_ttl_minutes | 0 | Chưa tự hết hạn PENDING nếu chưa bật TTL |
| spa.booking.cancel_cutoff_minutes / reschedule_cutoff_minutes | 0 | Khách tự hủy/đổi trước giờ bắt đầu; staff xử lý ngoại lệ có lý do |
| spa.booking.no_show_grace_minutes | 0 | Không no-show trước giờ; chủ Spa cần chọn grace phù hợp |
| spa.booking.no_show_quota_action | FORFEIT | Giữ tác động quota cũ nhưng ghi loại phạt riêng; có thể chọn RELEASE cho booking mới |
| spa.ticket.expiry_check_mode | BOOKING_TIME | Giữ điều khoản cũ; SERVICE_START là lựa chọn mới được chụp khi mua, không áp ngược lên vé đã bán |
| spa.booking.policy_version | legacy-compatible-v1 | Cần đặt version có ý nghĩa khi chốt bộ tham số mới |
| app.spa.notifications.enabled | false | Chưa gửi reminder/follow-up tự động |

Default không yêu cầu form/ack cho đến khi cấu hình theo dịch vụ. Role STAFF cũ còn quyền rộng để giữ tương thích; cần chuyển các tài khoản nhân viên thực tế sang SPA_RECEPTION/SPA_THERAPIST theo công việc và gỡ STAFF khi cần giới hạn quyền.

Các thay đổi client cần cập nhật: item execution và check-in trước hoàn tất; trạng thái order COMPLETED và paymentMethod CASH cho invoice Spa; header idempotency của invoice/cash; reason/idempotencyKey của gia hạn/bù lượt; lý do staff hủy/đổi lịch. API cũ không còn cho đánh dấu toàn buổi hoàn tất thay cho bằng chứng từng mục.

## Migration và kiểm chứng

- V31: execution/check-in/policy, reserved counters, sổ lượt và action history. Preflight dừng khi số dư/quota lịch cũ không thể đối chiếu; chỉ chuyển những reservations đang mở có bằng chứng. Không xóa hoặc gộp quyền lợi tự động.
- V32: invoice buổi lẻ, liên kết appointment và snapshot invoice item, bằng chứng phê duyệt hoàn.
- V33: role, form/care snapshot/evidence, hướng dẫn và dedup thông báo.
- V34: loại/capacity tài nguyên, yêu cầu và phân bổ/bảo trì.

Chạy [preflight chỉ đọc](ops/preflight_spa_workflows.sql) trên bản sao dữ liệu trước nâng cấp; các vấn đề checksum migration cũ vẫn theo [hướng dẫn đợt trước](BACKEND_FIX_REPORT_2026-10-02.md). Không tự repair checksum trên hệ thống vận hành.

Kết quả cuối được tổng hợp từ hồi quy toàn backend và kết quả mới nhất của các suite chạy lại sau sửa: **77 suite, 326 test; 325 đạt, 0 failure, 0 error, 1 skip**. Trong đó **61 test trên MySQL đạt**, gồm cả kiểm tra migration. Test bị bỏ qua trên H2 là truy vấn grouping theo thời gian đặc thù MySQL; bản MySQL tương ứng đã đạt.

Lượt kiểm tra lại cuối lúc 15:23:24 ngày 02/10/2026 chạy 47 test, 46 đạt và 1 skip, **BUILD SUCCESS**. Lượt hồi quy toàn bộ trước đó chạy 322 test; hai failure do ArchUnit nhập nhầm test fixture từ thư mục build tùy chỉnh đã được sửa bộ lọc và kiểm lại đủ sáu quy tắc kiến trúc. Bốn test mới về policy/phân quyền được bổ sung sau lượt toàn bộ. Số 326 là tổng hợp kết quả gần nhất từng suite, không phải một lượt toàn bộ mới. [Kết quả từng suite](SPA_VALIDATION_RESULTS_2026-10-02.json) ghi rõ nguồn và thời điểm.

Chạy lại từ thư mục backend bằng `powershell -ExecutionPolicy Bypass -File .\ops\verify_spa_backend.ps1`; thêm `-Tests 'TênTest1,TênTest2'` để chọn suite. [Script kiểm chứng](ops/verify_spa_backend.ps1) dùng thư mục `target-spa-validation` riêng, tránh IDE rebuild `target/classes` trong khi kiểm thử. Các test MySQL cần Docker; log lần chạy nằm tại `spa-final-tests.log` và `spa-final-recheck-tests.log` trong workspace, không đưa vào Git.

Các lớp kiểm chứng chính: SpaVisitExecutionIntegrationTest và MySQL tương ứng; SpaVisitCheckoutIntegrityIntegrationTest và MySQL tương ứng; FacilitySchedulingIntegrationTest và MySQL tương ứng; SpaPreparationServiceTest; CustomerCarePaginationTest; SpaNotificationEmailTest; OperationalMigrationIntegrityTest; dashboard H2/MySQL. Có hồi quy toàn bộ backend, bao gồm kiểm tra kiến trúc module và các sửa lỗi quản trị trước đó.
