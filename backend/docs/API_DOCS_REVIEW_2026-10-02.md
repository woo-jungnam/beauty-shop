# Đối chiếu API và tổ chức Swagger — 02/10/2026

Phạm vi: controller/DTO/quyền/validation/response thực của backend, OpenAPI sinh lúc runtime và tài liệu tích hợp. Không thay đường dẫn API nghiệp vụ để phục vụ việc phân nhóm tài liệu. Không triển khai backend hoặc chạy migration trên database vận hành.

## Những sai lệch đã sửa

| Vấn đề | Kết quả |
| --- | --- |
| JWT áp toàn bộ Swagger, kể cả login/catalog công khai | Security theo operation: public, guest/JWT tùy chọn, JWT bắt buộc và SePay key riêng. Profile trong auth vẫn bắt buộc JWT |
| Nhóm tên dài, số thứ tự 1/10/11 sắp sai; admin chứa GET công khai nhưng thiếu thao tác staff ngoài admin path | 13 nhóm ID hai chữ số, nhãn tiếng Việt; nhóm quản trị lọc method guard/path. Tag xếp theo module, operation theo path |
| Nhóm Spa/CRM/payment thiếu luồng mới hoặc liên quan | Đưa preparation/follow-up, resource, invoice/cash và spa-financials vào đúng các nhóm liên quan |
| Các API mới chưa có mô tả hoặc ghi sai quyền/trạng thái | Bổ sung Operation/Parameter/Schema từ code; tách vai trò khỏi ownership/assignment; không ghi PERFORMED cho legacy/no-show, không hứa backend tự chuyển tiền hoàn |
| Header idempotency, pagination, upload và schema record chưa rõ | Header và key body ở đúng endpoint; Pageable thành query page/size/sort; file binary multipart; schema DTO/nested records có tên và điều kiện rõ hơn |
| Operation ID trùng giữa controller hoặc alias | Sinh ID ổn định theo method/path và sao chép operation cho mỗi đường dẫn; không dùng ID phương thức Java chung như list/create/getAllOrders. Client sinh từ OpenAPI cần cập nhật theo ID mới |
| Timestamp LocalDateTime bị khai là date-time RFC3339 | Schema response thành công/lỗi dùng string không offset, giữ đúng định dạng wire thay vì khiến SDK parse thành OffsetDateTime |
| JSON success bị ghi media type */* | Đặt default produces của tài liệu là application/json; SSE và schema chatbot giữ produces riêng. Kiểm tra lại schema response theo đúng media type |
| HTTP thành công bị suy từ body.status hoặc mô tả sai | Ghi theo HTTP thực. Những API hiện hữu HTTP 200/body.status=201 được ghi rõ; API thực 201 có response metadata tương ứng |
| Thiếu required header/multipart bị fallback 500 | GlobalExceptionHandler trả 400/SYS_002; kiểm bằng request MockMvc có role hợp lệ |
| Update description thuộc tính không lưu | Thêm persistence description và V35 nullable TEXT; payload null giữ mô tả cũ. ID thuộc tính ở path là nguồn cho endpoint thêm value |
| Xóa kho có thể làm mất phạm vi vận hành của hàng hiện hữu | Guard giữ mutex kho/lô, từ chối khi còn quantity/reserved/quarantine; kiểm cả dữ liệu legacy soft-delete và transaction đồng thời nhận hàng/xóa kho |
| Mô tả dashboard dễ hiểu thành doanh thu thực hiện/capacity thực | Ghi gross receipts/hoàn riêng, kỳ [from,to), ngày Việt Nam, phút kế hoạch/actual/legacy riêng và công nợ hiện tại |
| Swagger có thông tin liên hệ/license giả định | Bỏ thông tin chưa được xác nhận; dùng mô tả đúng phạm vi dự án |

## Cách tra cứu sau sắp xếp

[Mục lục](README.md) → [hướng dẫn](API_GUIDE.md) → [danh mục endpoint](API_ENDPOINTS.md) hoặc Swagger runtime. [OpenAPI JSON](openapi.json) là snapshot đã đối chiếu controller; cập nhật bằng test và generator khi thay contract.

URL nhóm tài liệu đổi sang `/v3/api-docs/00-all` đến `/v3/api-docs/12-chatbot`; các bookmark nhóm cũ cần cập nhật. Danh sách đầy đủ trong API_GUIDE. API nghiệp vụ vẫn theo controller hiện có.

## Kiểm chứng

Các số liệu 66/16 bên dưới là kết quả đợt API/Swagger trước khi bổ sung search sản phẩm. Snapshot hiện tại được sinh lại sau thay đổi search; tham số và bằng chứng kiểm chứng mới nhất được liên kết tại [tài liệu tìm kiếm sản phẩm](PRODUCT_SEARCH.md). Hash trong JSON của đợt trước ghi nhận snapshot tại thời điểm đó.

Đối chiếu runtime **253 thao tác HTTP / 179 đường dẫn**, thuộc 45 controller. Có 13 nhóm Swagger, mọi nhóm truy cập được, có endpoint và không còn `$ref` thiếu; operation ID duy nhất trên từng method/path, bao gồm alias. Pattern đường dẫn Spring có regex được chuẩn hóa sang tham số OpenAPI khi đối chiếu.

| Suite | Test đạt | Nội dung |
| --- | --- | --- |
| OpenApiContractIntegrationTest | 6 | Toàn bộ mapping; summary/tag/ID/ref; security; nhóm; header/multipart/pagination/status/timestamp; request thiếu header/file; export |
| ComprehensiveSwaggerAndBearerApiTest | 10 | Swagger UI/JSON, 13 nhóm, JWT/role/public/guest và key SePay qua HTTP |
| ProductAttributeServiceIntegrationTest | 4 | CRUD/đọc lại description, update null giữ giá trị, ID path/body/internal và HTTP CATALOG_STAFF |
| CheckoutIntegrityIntegrationTest | 19 | Hồi quy checkout và 5 tình huống xóa kho, gồm hai chiều race nhận hàng/xóa |
| MySqlCheckoutIntegrityTest | 19 | Cùng các luồng trên MySQL; Flyway lên V35 và Hibernate validate schema |
| GlobalExceptionHandlerTest | 2 | Không lộ dữ liệu nhạy cảm qua lỗi |
| ModularArchitectureTest | 6 | Quy tắc kiến trúc module |
| **Tổng kết quả đợt API/Swagger** | **66** | **0 failure, 0 error, 0 skip** |

Lượt cuối ngày 02/10/2026 lúc 17:25:05: **16 test OpenAPI/Swagger đạt, BUILD SUCCESS**. Các suite nghiệp vụ/H2/MySQL và kiến trúc đã đạt trong lượt 66 test trước đó; sau khi sửa các lỗi metadata, chỉ chạy lại hai suite OpenAPI/Swagger chịu ảnh hưởng. Bảng là kết quả gần nhất từng suite, không phải một lượt 66 test mới. [Bằng chứng từng suite và hash snapshot](API_VALIDATION_RESULTS_2026-10-02.json); log workspace `spa-api-docs-tests.log`, `spa-api-docs-final-tests.log` được giữ ngoài Git.

Phạm vi kiểm chứng gồm đối chiếu cấu trúc của toàn bộ API và request HTTP đại diện cho các hợp đồng quan trọng; không diễn giải thành đã chạy mọi payload/nhánh nghiệp vụ của 253 thao tác. [Hướng dẫn chạy test/sinh tài liệu](API_GUIDE.md) dùng thư mục build riêng để tránh IDE thay class trong lúc kiểm tra. Tài liệu snapshot được sinh từ suite đã đạt; generator từ chối khi nguồn thay đổi sau xác minh hoặc snapshot mới hơn report đã đạt.

## Giới hạn cần biết

Các điều kiện dynamic tại service không thể suy hết từ JSON Schema; operation ghi rõ các yêu cầu quan trọng và tài liệu BA ghi khoảng trống. Metadata không cấp thêm quyền. Những nghiệp vụ chưa triển khai như dừng dịch vụ giữa chừng, thay dịch vụ trong buổi, rút consent và chính sách cọc/hoàn gói một phần vẫn theo [báo cáo BA](../SPA_BUSINESS_REVIEW_2026-10-02.md).

Thông báo Spa mặc định tắt; dedup khi đổi lịch A→B→A, readiness SMTP và replay vẫn có giới hạn đã ghi trong [care workflow](../SPA_CARE_WORKFLOW_2026-10-02.md). Tài liệu không khẳng định các giới hạn đó đã được sửa trong đợt API/Swagger này.

V35 cần được áp cùng phiên bản code có description khi nâng cấp; migration MySQL được kiểm trong database test, không tự sửa dữ liệu hoặc checksum production. HTTP 200/body.status=201 ở các API tạo hiện hữu được mô tả theo thực tế, chưa đồng nhất toàn bộ hành vi bằng thay đổi status ngoài phạm vi cần sửa.
