# Quyền truy cập, biểu mẫu và chăm sóc sau buổi Spa

Tài liệu này mô tả phần backend đã triển khai ngày 02/10/2026. Kết quả kiểm thử chung được đối chiếu với báo cáo nghiệm thu của lần chạy Maven cuối; những giới hạn vận hành bên dưới vẫn còn cần xử lý.

## Vai trò và luồng sử dụng

| Vai trò | Xem lịch/biểu mẫu | Đọc care-summary nội bộ | Thực hiện item và xác nhận đã xem lưu ý | Tạo template/policy hoặc override thiếu chuẩn bị |
| --- | --- | --- | --- | --- |
| ADMIN | Mọi lịch | Có | Có | Có; override phải có lý do |
| STAFF hiện có | Mọi lịch, quyền lễ tân hiện có | Có | Có, theo quyền tương thích hiện có | Không |
| SPA_RECEPTION | Mọi lịch và biểu mẫu để điều phối | Có | Không | Không |
| SPA_THERAPIST | Chỉ lịch có item chưa xóa gán Staff.userId đúng tài khoản | Chỉ lịch được giao | Chỉ item được giao | Không |
| CS_STAFF | CRM theo phân quyền hiện có | Có | Không | Không |
| CUSTOMER / USER cũ | Chỉ lịch sở hữu; tự nộp câu trả lời/xác nhận | Không đọc ghi chú nội bộ qua API này | Không | Không |

Hai role mới được seed trong V33 và nằm trong tập role hệ thống bất biến. Nhân viên không thể thay customerId khi gửi form: backend lấy chủ lịch từ cơ sở dữ liệu, lấy actor từ phiên hiện hành và buộc hai ID giống nhau. ADMIN cũng không được gửi thay khách khác.

Khi chuyển tài khoản nhân viên sang SPA_RECEPTION/SPA_THERAPIST, phải gỡ ROLE_STAFF nếu muốn áp dụng phạm vi quyền mới. Chỉ thêm role mới vào tài khoản còn ROLE_STAFF vẫn giữ quyền rộng của STAFF. Tài khoản kỹ thuật viên cần liên kết đúng Staff.userId để nhận quyền trên các lịch được giao.

## Yêu cầu trước khi bắt đầu

ADMIN tạo template, sau đó mỗi lần sửa tạo một version mới; không có API sửa bản đã tồn tại. Chính sách theo service gồm danh sách form-version bắt buộc và cờ warningsRequired. Những liên kết này nằm trong bảng riêng, không sửa BeautyService.

Sau khi book đã lưu item ID, core gọi snapshotRequirements. Mỗi item lưu cờ cảnh báo và các form-version bắt buộc tại thời điểm đó, kể cả marker khi không có yêu cầu. Thay template hoặc policy sau này chỉ ảnh hưởng booking mới. Booking cũ không có marker tiếp tục được phục vụ theo luồng legacy; không tự gắn yêu cầu mới vào lịch cũ.

Khách có thể nộp lại câu trả lời trước khi item bắt đầu; mỗi lần là một bản ghi mới với version yêu cầu đã chụp, customer/actor, thời điểm và acknowledged=true. Nhân viên xem care-summary gồm ghi chú CRM và các câu trả lời theo version đã đặt. Hash phiên bản bao phủ cả hai nguồn: thêm/sửa/xóa ghi chú hoặc khách nộp lại form làm xác nhận đã xem cũ hết hiệu lực. Tạo version template mới không đổi nội dung mà lịch cũ phải xác nhận.

Khi START, core kiểm tra đủ form có xác nhận của chính khách và, nếu warningsRequired=true, xác nhận của chính người bắt đầu trên hash care-summary hiện tại. Default không có form, warningsRequired=false. ADMIN có thể tạo ngoại lệ với lý do; ngoại lệ lưu riêng, không tạo bản ghi giả rằng khách đã đồng ý. Lịch đang IN_PROGRESS vẫn cho chuẩn bị các item PLANNED phía sau; evidence của item đã bắt đầu/kết thúc không được sửa bằng các API này.

API hiện chỉ nhận acknowledged=true; chưa hỗ trợ ghi từ chối hoặc rút lại consent, cũng chưa có API revoke override của ADMIN trước START. Đây là các thao tác BA cần thiết kế thêm với actor/thời điểm/lý do và tác động đến readiness. Override hiện cho phép bỏ qua toàn bộ yêu cầu chuẩn bị của item đã được chọn.

## API bổ sung

| Phương thức | Đường dẫn | Mục đích |
| --- | --- | --- |
| GET / POST | /api/v1/admin/spa/preparation/templates | Phân trang template hiện hành / tạo template |
| GET / POST | /api/v1/admin/spa/preparation/templates/{id}/versions | Lịch sử version / tạo version mới |
| GET / PUT | /api/v1/admin/spa/preparation/services/{id} | Chính sách form/cảnh báo theo service |
| GET | /api/v1/spa/appointments/{id}/care-summary | Nội dung chăm sóc nội bộ và versionHash |
| GET | /api/v1/spa/appointments/{id}/preparation | Snapshot yêu cầu, latest response, lịch sử ack/override |
| POST | /api/v1/spa/appointments/{id}/preparation/forms/{requirementId}/responses | Khách nộp answers và acknowledged |
| POST | /api/v1/spa/appointments/{id}/preparation/items/{itemId}/acknowledge-warnings | Người thực hiện gửi versionHash đã xem |
| POST | /api/v1/spa/appointments/{id}/preparation/items/{itemId}/override | ADMIN ghi reason ngoại lệ |
| GET | /api/v1/admin/crm/customers/page | keyword, page=0, size=20; tổng phần tử/trang |
| GET | /api/v1/admin/crm/customers/{id}/notes/page | Phân trang lịch sử chăm sóc |
| GET | /api/v1/spa/appointments/{id}/follow-up-instructions | Xem hướng dẫn nhân viên đã viết, theo quyền lịch |
| POST | /api/v1/spa/appointments/{id}/follow-up-instructions/items/{itemId} | Người được phân công viết content và dueAt sau item PERFORMED/LEGACY_FINALIZED |

CRM giữ hai API list cũ với tối đa 100 phần tử; client dùng API page để truy cập các bản ghi phía sau. Page phải không âm, size 1–100, offset không tràn; keyword tối đa 100 ký tự và dấu %/_ được tìm như ký tự thường. Form hỗ trợ TEXT và BOOLEAN, key duy nhất, đáp án theo đúng định nghĩa và các trường bắt buộc.

`id` trong đường dẫn preparation/follow-up là appointmentId. `requirementId` lấy từ `GET preparation` (`forms[].id` của item), không phải templateId hoặc formVersionId. ADMIN cấu hình service bằng `requiredFormVersionIds`, tối đa 20 ID, không trùng và chỉ một version mỗi template; `[]` bỏ form cho booking mới. Tạo template cần title 1–150 ký tự và 1–50 câu hỏi; mỗi key là `[A-Za-z][A-Za-z0-9_]{0,49}`, label 1–500 ký tự. TEXT answer tối đa 4000 ký tự; BOOLEAN answer dùng JSON true/false; tổng answers tối đa 50 key, JSON tối đa 30000 ký tự và không có key ngoài version đã đặt.

Ví dụ khách nộp form: `{"acknowledged":true,"answers":{"skinGoals":"Dưỡng ẩm"}}`. Người thực hiện gửi `{"versionHash":"<SHA-256 từ care-summary>"}`; ADMIN override gửi `{"reason":"Lý do ngoại lệ được duyệt"}` với reason 1–1000 ký tự. Các evidence POST trả HTTP 201, tạo bản mới append-only; không có `Idempotency-Key` hoặc dedup cho việc nộp lại form/ack/override. Lịch phải PENDING/CONFIRMED/IN_PROGRESS và item vẫn PLANNED chưa START; khách không được chỉnh evidence của item đã thực hiện.

POST follow-up nhận `{"content":"Hướng dẫn do nhân viên viết","dueAt":"2026-10-06T02:00:00Z"}`: content 1–4000 ký tự, dueAt là Instant có offset/UTC trong vòng một năm, cho phép dung sai một phút trước hiện tại. HTTP 201 khi lưu; chưa là bằng chứng email đã gửi. Follow-up GET không phân trang. CRM chỉ cho ADMIN/CS_STAFF; STAFF/SPA_RECEPTION không tự có quyền các endpoint `/api/v1/admin/crm`. POST CRM notes hiện trả HTTP 200 với `ApiResponse.status=201`; `followUpAt` trong care note chỉ lưu dữ liệu, không tự tạo Spa follow-up instruction hoặc gửi mail.

## Nhắc lịch và hướng dẫn sau buổi

Tự động gửi mặc định tắt: app.spa.notifications.enabled=false. Khi bật, app.jobs.enabled cũng phải bật để scheduler enqueue; vận chuyển email dùng app.mail.enabled và SMTP hiện có. reminder-hours mặc định 24, cho phép 1–168; poll-interval-ms mặc định 60000. Chưa bật SMTP thì cơ chế mail hiện có chỉ ghi nhận thông báo trong log.

Trước khi bật notifications, cần cấu hình SMTP và kiểm tra gửi email thực tế thành công. Nếu app.mail.enabled=false hoặc thiếu mail sender, sendEmail trả thành công sau khi ghi log; consumer vẫn commit receipt và producer đã lưu dedup. Bật SMTP sau đó không tự replay các event đã xử lý theo chế độ chỉ ghi log. Quy trình gửi lại và kiểm tra trạng thái gửi cần được xác định trước vận hành; hiện chưa có API replay riêng cho trường hợp này.

Reminder chỉ áp dụng CONFIRMED trong khoảng tới, chưa check-in. Producer khóa lịch và ghi dedup cùng outbox trong một transaction; khóa dedup gồm appointmentId, ngày và giờ. Một cặp ngày/giờ khác có thể tạo reminder khác, nhưng A→B→A dùng lại khóa A: nếu event A trước đã bị consumer bỏ qua khi lịch ở B, quay về A có thể không có reminder mới. Hiện chưa có API tạo revision/requeue để xử lý trường hợp này.

Consumer kiểm tra lại status, lịch hiện hành, check-in và giờ bắt đầu trước gửi; event bị bỏ qua nếu dữ liệu đọc khi consumer kiểm tra đã thể hiện lịch hủy/đổi. Không giữ khóa lịch xuyên suốt thao tác SMTP, nên không bảo đảm chống mọi thay đổi xảy ra sau kiểm tra và ngay trước gửi. Follow-up chỉ lấy nội dung riêng nhân viên đã nhập trên item đã thực hiện; chỉ enqueue sau khi toàn lịch COMPLETED và đến dueAt. Private CRM notes và đáp án form không nằm trong event thông báo.

Event qua outbox, NotificationInbox chống xử lý lặp theo event-id; lỗi SMTP được propagate để rollback receipt và retry. Nội dung HTML của email Spa được escape. Không có chẩn đoán hoặc sinh khuyến nghị y khoa tự động.

JDBC bind/read TIMESTAMP và TIME dùng Calendar UTC để khớp cấu hình Hibernate. Reminder phân trang ứng viên theo ngày/ID rồi lọc khoảng Instant bằng lịch đã hydrate qua ORM, tránh sai so sánh TIME khi chuyển giờ UTC đi qua nửa đêm; dispatch TIME cũng được bind như ORM để dedup khớp.

Phạm vi hiện tại: giao diện chưa được bổ sung trong công việc backend này; API preparation trả câu trả lời mới nhất, các lần nộp trước vẫn lưu append-only trong database. Quản lý DLT và rút lại/sửa lịch gửi instruction chưa có màn hình/API riêng. Việc gửi SMTP và commit receipt có khoảng lỗi khi tiến trình chết sau gửi nhưng trước commit, nên không tuyên bố exactly-once email tuyệt đối.

## Kiểm thử bổ sung

SpaPreparationServiceTest thực thi SQL trên H2 với chính V33, kiểm tra scope owner/assigned, immutable version và snapshot, không giả đồng ý, ack theo actor/hash, form edit làm stale ack, override reason/history, legacy/default không bị chặn, chuẩn bị item sau khi lịch IN_PROGRESS, reminder dedup và event cũ, follow-up theo staff và rollback/retry outbox.

CustomerCarePaginationTest dùng 105 khách để xác nhận không mất dữ liệu sau giới hạn cũ, page/tổng ổn định và validation offset/keyword. SpaNotificationEmailTest kiểm tra hướng dẫn và tên khách có HTML được hiển thị như văn bản. Metadata JPA của 11 bảng tương ứng V33 hỗ trợ cả Hibernate create-drop trong test và validate khi triển khai Flyway.

Kiểm chứng cuối: BUILD SUCCESS. Tổng hợp 77 suite, 326 test: 325 đạt và 1 skip; 61 test MySQL đạt. Trong phần này, SpaPreparationServiceTest có 11 trường hợp đạt, CustomerCarePaginationTest có 2 và SpaNotificationEmailTest có 1. Công việc chỉ chạy migration trên database kiểm thử, chưa triển khai lên database vận hành.
