# Hướng dẫn API và Swagger

Base path nghiệp vụ: `/api/v1`. Swagger UI: `/swagger-ui.html` hoặc `/swagger-ui/index.html`. OpenAPI chung: `/v3/api-docs`; cấu hình danh sách nhóm: `/v3/api-docs/swagger-config`. URL máy chủ trong Swagger là `/`, dùng backend/reverse proxy hiện tại.

## Thứ tự nhóm tài liệu

| ID / URL OpenAPI | Nội dung |
| --- | --- |
| /v3/api-docs/00-all | Tất cả API |
| /v3/api-docs/01-admin | Quản trị và nhân viên: endpoint quản trị, guard có ADMIN và các action chăm sóc Spa kiểm quyền tại service; không đưa GET catalog công khai vào cùng nhóm |
| /v3/api-docs/02-identity | Đăng ký/đăng nhập, token, profile, quản lý tài khoản và role |
| /v3/api-docs/03-catalog | Sản phẩm, SKU, danh mục, thương hiệu, thuộc tính, tag, ingredient và media |
| /v3/api-docs/04-orders | Giỏ hàng, checkout sản phẩm, đơn hàng và xác nhận đã hoàn tiền |
| /v3/api-docs/05-payments | Webhook, giao dịch, đối soát và thanh toán invoice Spa |
| /v3/api-docs/06-spa | Dịch vụ/gói/vé, booking, check-in, execution, resource và chỉ số Spa |
| /v3/api-docs/07-crm | Hồ sơ khách, form chuẩn bị, cảnh báo và chăm sóc sau buổi |
| /v3/api-docs/08-inventory | Kho, tồn/lô, kiểm kê, cách ly, chuyển kho và mua hàng |
| /v3/api-docs/09-promotions | Voucher và chính sách giảm giá |
| /v3/api-docs/10-reviews | Đánh giá công khai và kiểm duyệt |
| /v3/api-docs/11-operations | Dashboard, cấu hình nghiệp vụ, kiểm toán và cảnh báo |
| /v3/api-docs/12-chatbot | Chat JSON/SSE, health, schema của dịch vụ chatbot và thao tác ADMIN |

Các nghiệp vụ liên quan có thể xuất hiện ở nhiều nhóm, ví dụ invoice Spa ở nhóm Spa và thanh toán; đây là cùng endpoint. Thứ tự nhóm dùng ID có hai chữ số; tag được xếp theo module, endpoint theo đường dẫn. URL nhóm Swagger cũ có tên dài đã được thay bởi ID trong bảng; **đường dẫn API nghiệp vụ giữ theo controller**.

## Xác thực và quyền

- **Công khai:** không bắt buộc JWT, như login/register/refresh, GET catalog, GET dịch vụ Spa và GET đánh giá. Profile trong `/auth` vẫn cần JWT.
- **Khách vãng lai hoặc đã đăng nhập:** phần lớn cart, checkout sản phẩm và đọc đơn theo ID cho JWT tùy chọn; riêng `POST /cart/merge` bắt buộc JWT. Phiên khách vãng lai phải có `sessionId` theo từng endpoint. Đọc đơn còn kiểm chủ sở hữu/phiên, không được coi là công khai dữ liệu đơn hàng.
- **JWT:** gọi login, lấy `data.accessToken`. Trong Swagger Authorize → bearerAuth chỉ dán token; Swagger tự thêm `Bearer`. Refresh token chỉ gửi cho API refresh/logout đúng contract.
- **SePay:** webhook dùng scheme `sepayApiKey`, header `Authorization: Apikey <secret>` theo cấu hình backend. Không dùng accessToken khách hàng thay key webhook. Không cấu hình cả hai scheme cho cùng thao tác webhook.

Vai trò được kiểm tại controller và filter; service còn kiểm chủ sở hữu, phân công, trạng thái và quyền trên tài nguyên. Các metadata `x-access-mode`, `x-module`, `x-method-authorization` trong JSON hỗ trợ đối chiếu, không thay kiểm quyền backend. Nhân viên chuyển sang SPA_RECEPTION/SPA_THERAPIST phải gỡ STAFF nếu muốn thu hẹp quyền.

## Request và response

JSON thông thường dùng `Content-Type: application/json`. Upload media dùng `multipart/form-data`, trường **file** kiểu binary; xem giới hạn định dạng/kích thước trong operation. Các header `Idempotency-Key` được đánh dấu ở đúng API, không phải yêu cầu chung cho mọi request. Admin gia hạn/bù lượt vé dùng key trong body theo schema.

Phản hồi thông thường có `status`, `message`, `data`, `timestamp`, `path`; lỗi có thể có `errorCode`/`errors`. Trường null của ApiResponse được bỏ qua. `timestamp` là LocalDateTime máy chủ, không có offset; thời điểm nghiệp vụ dạng Instant dùng ISO 8601 có offset/Z. **HTTP status thực trên response là nguồn quyết định**: một số API tạo hiện hữu trả HTTP 200 nhưng body.status=201, được ghi rõ trong operation; không suy HTTP 201 chỉ từ trường body.

| HTTP | Ý nghĩa |
| --- | --- |
| 200 / 201 | Thành công theo operation; kiểm schema dữ liệu cụ thể |
| 400 | JSON/kiểu/tham số/header/multipart thiếu hoặc vi phạm nghiệp vụ được mapping 400 |
| 401 | Thiếu/sai/hết hạn xác thực hoặc key webhook |
| 403 | Không đủ role, quyền sở hữu hoặc phạm vi được giao |
| 404 | Không tìm thấy tài nguyên |
| 409 | Xung đột trạng thái/dữ liệu/đồng thời; một số ErrorCode nghiệp vụ mapping 409 |
| 415 | Sai Content-Type |
| 500 | Lỗi nội bộ; không trả chi tiết stack trace |

Mã lỗi cụ thể theo ErrorCode và từng operation. Validation không trả lại giá trị bị từ chối để tránh lộ mật khẩu/thông tin cá nhân. SSE `/chatbot/chat/stream` trả `text/event-stream`; `/chatbot/openapi.json` trả schema JSON của upstream, không bọc ApiResponse. Lỗi sau khi stream đã bắt đầu cần xử lý theo sự kiện, không chỉ HTTP status ban đầu.

Phân trang dùng `page` bắt đầu **0**, `size`, `sort=field,asc|desc` ở endpoint dùng Pageable. CRM có page/size riêng, size 1–100, không tự hỗ trợ sort nếu contract không khai báo. Mỗi endpoint ghi các bộ lọc cụ thể; không gửi object `pageable` trong query. Khoảng Instant dashboard là `[from,to)`; ngày Spa occupancy bao gồm cả from và to theo giờ Việt Nam.

## Trình tự tích hợp chính

Tìm kiếm mỹ phẩm dùng `GET /api/v1/products/search`: từ khóa tùy chọn, kết hợp thương hiệu/danh mục/giá/tồn và các tiêu chí chăm sóc da, phân trang sau khi lọc tại database. Chi tiết tham số, sort và giá/tồn cùng SKU tại [tài liệu tìm kiếm sản phẩm](PRODUCT_SEARCH.md).

1. **Mỹ phẩm:** GET catalog → cart theo user/session → POST orders/checkout → thanh toán BANK/COD → nhân viên xử lý đơn và giao hàng. Giá/tồn được backend tính lại; header Idempotency-Key của checkout là tùy chọn, khuyến nghị gửi và giữ cùng key/body khi retry.
2. **Gói Spa:** xem service/package → tạo purchase order → đối soát paid → cấp ticket theo snapshot. Việc sở hữu đơn chưa trả không tạo quyền redeem.
3. **Buổi Spa:** book PENDING → lễ tân phân công/xác nhận → khách nộp form và nhân viên acknowledgment nếu cấu hình → check-in → IN_PROGRESS buổi → START/PERFORMED hoặc SKIPPED từng item → COMPLETED → invoice các item PERFORMED ngoài vé → thu CASH/BANK và đối soát. Các trường quota/execution/legacy được phân biệt trong schema; không lấy `isTicketUsed` làm bằng chứng đã thực hiện.
4. **Ngoại lệ:** cancel/reschedule chỉ ở trạng thái cho phép, reason theo quyền/cutoff; NO_SHOW cần thời điểm/grace và chưa check-in. Gia hạn/bù lượt yêu cầu reason và key ổn định. Hoàn tiền là ghi nhận ADMIN xác nhận đã hoàn thủ công theo API, không tự chuyển tiền.
5. **Kho:** tạo/nhập lô và kiểm chất lượng → giữ/xuất theo nghiệp vụ đơn → kiểm kê/cách ly/chuyển kho. Không xóa kho khi vẫn còn tồn, giữ chỗ hoặc cách ly; xử lý hàng trước khi xóa kho. Guard này không áp cho thao tác isActive=false.

Các chi tiết action, quyền và trạng thái nằm tại [danh mục endpoint](API_ENDPOINTS.md) và các tài liệu Spa liên kết trong [mục lục](README.md). Các nghiệp vụ chưa có endpoint được ghi trong báo cáo BA, không đưa vào Swagger như khả năng đã triển khai.

## Kiểm chứng và sinh snapshot

Từ thư mục backend chạy `powershell -ExecutionPolicy Bypass -File .\ops\verify_spa_backend.ps1 -Tests 'OpenApiContractIntegrationTest,ComprehensiveSwaggerAndBearerApiTest'`. Profile test dùng H2, tắt jobs/listener và không gửi email. Test đối chiếu controller mapping với OpenAPI, kiểm `$ref`, security, nhóm, headers, multipart, pagination và HTTP thực; ghi snapshot vào `target-spa-validation/api-docs/`.

Sau khi test đạt, chạy `python .\ops\generate_api_docs.py` để cập nhật `docs/openapi.json` và `docs/API_ENDPOINTS.md` từ snapshot vừa kiểm chứng. Không dùng snapshot cũ khi controller/DTO đã thay đổi. Swagger runtime phản ánh backend đang chạy; snapshot không chứng minh các môi trường đã được nâng cấp.
