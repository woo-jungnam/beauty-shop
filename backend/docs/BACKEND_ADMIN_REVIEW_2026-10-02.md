# Báo cáo rà soát backend quản trị — 02/10/2026

## 1. Kết luận

**Backend đã có phần lớn các nhóm quản lý chính, nhưng chưa đầy đủ ở các luồng chi tiết và chưa thể kết luận logic đã đúng để vận hành với dữ liệu thật.** Các vấn đề đáng ưu tiên là migration có thể phá dữ liệu, lỗi đọc cache sản phẩm, lỗi kiểm duyệt đánh giá, thu hồi quyền không nhất quán, nhập kho đồng thời và liên kết ca làm việc với đặt lịch Spa.

Các cơ chế nền tảng đã được triển khai khá rõ: API theo module, phân quyền, transaction, khóa dữ liệu, checkout có idempotency, phân bổ tồn theo đơn/lô, snapshot giá/quyền sử dụng Spa, hoàn tiền và outbox. Tuy nhiên, có API và test không đồng nghĩa các luồng quản trị đã hoàn chỉnh.

**Kết quả kiểm thử hiện tại:** `mvnw.cmd test -B` thất bại. Tổng 138 test: **128 qua, 2 failure, 1 error, 7 bị bỏ qua**. Hai kiểm tra bổ sung trên H2 đã tái hiện lỗi đọc kết quả tổng hợp review và lỗi lazy loading của thuộc tính sản phẩm.

Đây là báo cáo kiểm tra mã nguồn hiện tại. Không sửa mã nghiệp vụ hoặc migration trong đợt rà soát này.

## 2. Phạm vi và mức độ bằng chứng

- Kiểm kê 376 file Java chính và 44 file Java test tại thời điểm rà soát. Đọc các controller, service, repository, DTO và migration liên quan các luồng quản trị trọng yếu; không tuyên bố đã kiểm toán từng dòng của toàn bộ backend.
- Phạm vi gồm identity, catalog, cart, order, payment, inventory, procurement, promotion, review, Spa, CRM, dashboard và các phần dùng chung về security, audit, cấu hình, notification/outbox, chatbot.
- Rà cả file đã được Git theo dõi, các thay đổi chưa commit và file mới. Kết luận không chỉ dựa trên phiên bản ở commit gần nhất.
- Đọc bổ sung menu/hợp đồng API frontend để nhận diện các mục quản lý và ý nghĩa hiển thị của KPI; không thực hiện kiểm thử giao diện hoặc review toàn frontend.
- Không đọc giá trị secret trong `.env`, không chạy migration V27 trên dữ liệu thật, không kiểm thử giao dịch SePay bên ngoài.
- Không tìm thấy `AGENTS.md` trong workspace.

Phân loại bằng chứng:

| Nhãn | Ý nghĩa |
|---|---|
| Tái hiện | Có kết quả chạy test hoặc kiểm tra thực tế trong lần rà soát này |
| Xác định từ code | Luồng và điều kiện xử lý thể hiện trực tiếp trong code; chưa gọi lại mọi API với tài khoản thật |
| Rủi ro đồng thời | Suy ra từ thứ tự đọc/khóa/ghi; cần kiểm tra bằng hai transaction trên MySQL |
| Rủi ro có điều kiện | Chỉ xảy ra với cấu hình triển khai hoặc dữ liệu cụ thể chưa được kiểm chứng |
| Cần chốt nghiệp vụ | Tính năng/chính sách chưa có hoặc chưa rõ; không tự động coi là bug |

P1 là việc phải ưu tiên trước khi nghiệm thu vận hành; P2 ảnh hưởng một luồng nghiệp vụ hoặc tính đúng đắn dữ liệu; P3 là hoàn thiện hợp đồng, khả năng vận hành hoặc hiệu năng. Mức độ nêu ở đây phản ánh ảnh hưởng tiềm năng, không phải xác nhận đã có sự cố trên môi trường thật.

## 3. Đánh giá tổng thể từng nhóm quản lý

| Nhóm | Đã có | Đánh giá phần chi tiết |
|---|---|---|
| Người dùng, vai trò, phiên đăng nhập | Danh sách/chi tiết, trạng thái, gán role, xem phiên, force logout, refresh rotation | Chưa bảo vệ admin cuối cùng; đổi/xóa role không thu hồi JWT. Chưa có tìm kiếm/lọc user và thu hồi riêng một phiên |
| Sản phẩm, SKU, ảnh | CRUD, danh sách/detail admin, trạng thái, giá theo SKU, soft delete, quản lý ảnh | Cache sản phẩm có lỗi deserialize; public detail chưa nhất quán trạng thái; kiểm tra tham chiếu chưa đầy đủ |
| Danh mục, thương hiệu, tag | CRUD, cache, kiểm tra chu trình danh mục trong luồng tuần tự | Cần chính sách xóa dữ liệu đang dùng, cây nhiều cấp và kiểm tra đồng thời; audit chưa bao phủ |
| Thuộc tính, ingredient | CRUD, mapping sản phẩm/biến thể, kiểm tra parent ở một số luồng | Thuộc tính lỗi lazy loading và bỏ qua datatype. Dữ liệu skin compatibility/concern/usage cần chốt mức quản trị mong muốn |
| Media | Upload, UUID tên file, giới hạn kích thước, dọn file khi rollback | Chưa có quản lý đầy đủ danh sách/xóa/đối soát file; kiểm tra nội dung SVG cần cải thiện |
| Kho, lô, tồn, ledger | Giữ/nhả/trừ/hoàn tồn, FEFO, kiểm định, nhập, điều chỉnh, điều chuyển | Nguy cơ mất cập nhật nhập kho; sai nghĩa tồn cách ly; nhận vào lô đã xóa không phục hồi; không nhập được sản phẩm nháp |
| Nhà cung cấp, phiếu mua | CRUD nhà cung cấp; DRAFT → APPROVED → RECEIVED/CANCELLED | Chưa kiểm tra SKU khi tạo/duyệt; chưa sửa được draft. Nhận từng phần, trả NCC, công nợ nằm ngoài baseline đã ghi trước đó |
| Đơn hàng, hoàn tiền | Checkout, bộ lọc admin, vòng đời đơn, xác nhận hoàn tiền | ORDER_STAFF bị chặn detail; thiếu nhánh đơn 0 đồng; job timeout có thể bị chặn; response detail chưa trả đủ lịch sử/snapshot |
| Thanh toán, đối soát | Webhook, chống trùng, cộng dồn tiền, danh sách/detail/summary | QR và tổng tiền phần lẻ không khớp; COD có thể ghi đè tiền đã nhận; summary chưa đủ để đối soát toàn dòng tiền |
| Voucher | CRUD, thời hạn, tổng quota, quota theo user, khóa khi áp dụng | Thiếu kiểm tra min/max tiền không âm; chính sách trả quota khi hủy đơn chưa được xác định |
| Review | Chỉ người mua đã giao được tạo; duyệt/từ chối/reply/xóa | Duyệt/xóa có lỗi đọc kết quả aggregate đã tái hiện; unit test mock che mất lỗi repository thật |
| Dịch vụ, gói, vé Spa | CRUD, purchase snapshot, cấp vé sau thanh toán, gia hạn/bù buổi | Vẫn bán gói chứa dịch vụ đã xóa; cache chưa đồng bộ; cho phép thời gian chuẩn bị/hạn gói âm; bộ lọc vé bỏ điều kiện |
| Lịch hẹn, nhân viên, ca, facility | Book/hủy/đổi/xác nhận, kỹ năng, chống trùng lịch, quản lý ca | Ca không tham gia điều kiện booking; slot có thể hiện trống dù không có nhân viên phù hợp; facility chưa có quản trị/capacity |
| CRM | Tìm khách, hồ sơ 360°, ghi chú da/dị ứng/chống chỉ định/follow-up | Search giới hạn 100, không phân trang; follow-up hiện là trường lưu dữ liệu; chưa nối cảnh báo/chống chỉ định vào các luồng khác |
| Dashboard, audit, cấu hình, cảnh báo | Overview/biểu đồ/top sản phẩm/Spa KPI, audit list/detail, config CRUD, alerts | Cần thống nhất nghĩa thực thu/doanh thu và công suất; audit bỏ sót route; cấu hình cận hạn không được sử dụng |
| Notification, outbox, chatbot | Sự kiện, claim/lease, dedup receipt, email, chat/SSE, admin sync | Email lỗi vẫn ghi nhận đã xử lý; thiếu công cụ phục hồi FAILED/DLT; status/cancel notification hiện chỉ log |

## 4. Các vấn đề ưu tiên cao

### F01 — P1: Migration V27 xóa dữ liệu vận hành và tái dùng ID

**Bằng chứng: xác định từ SQL; chưa chạy trên DB thật.** [V27](D:/khoaluantotnghiep/backend/src/main/resources/db/migration/V27__seed_authentic_medical_products.sql:8) tắt kiểm tra khóa ngoại. Các câu DELETE ở dòng 12–31 dùng điều kiện như `id > 25 OR id BETWEEN 1 AND 25`, tức bao phủ toàn bộ ID dương thông thường.

Migration xóa sản phẩm, biến thể, tồn kho, ledger, chi tiết phiếu mua, giỏ, đánh giá và các mapping; sau đó seed lại sản phẩm/biến thể bằng ID cũ. Dòng 392–402 nạp tồn mẫu 250/150 và giữ chỗ bằng 0. Các bản ghi `order_items`, `stock_allocations`, `product_attribute_values` còn lại có thể tham chiếu tới dữ liệu đã mất hoặc SKU khác được gán lại cùng ID.

**Ảnh hưởng:** mất lịch sử và tồn thật, lệch đơn đã bán, lệch reservation; DB vẫn có thể cho phép nhiều tham chiếu sai vì FK đã bị tắt khi thao tác. V28 không phục hồi dữ liệu này.

**Hướng xử lý:** tách seed/reset demo khỏi đường migration nâng cấp; bảo toàn ID và dữ liệu giao dịch. Nếu V27 đã áp dụng, cần kiểm tra lịch sử Flyway và dữ liệu thực tế trước khi chọn phương án phục hồi từ backup. Không suy ra trạng thái DB hiện tại chỉ từ file SQL.

### F02 — P1: Cache danh sách sản phẩm không đọc lại được JSON vừa ghi

**Bằng chứng: tái hiện trong regression.** [ProductListResponse](D:/khoaluantotnghiep/backend/src/main/java/com/core/beautyshop/modules/catalog/application/dto/response/ProductListResponse.java:90) có getter tương thích `getPrice()`/`getBasePrice()` nhưng không có trường/setter tương ứng. Jackson serialize thêm các trường này; deserialize bằng mapper strict trong cache thất bại với `Unrecognized field "price"`.

`CacheConfigTest.productPageCanRoundTripAsJson` lỗi đúng tại bước deserialize. `CacheConfig` production dùng cùng kiểu serializer và bản copy của ObjectMapper strict. Vì vậy, đường đọc cache sản phẩm Redis có thể lỗi khi cache đã có dữ liệu; test dùng cache local không chứng minh đường Redis an toàn.

**Hướng xử lý:** xác định rõ JSON contract cho getter tính toán: read-only/ignore hoặc ánh xạ tương thích khi đọc. Kiểm tra thêm ProductResponse nếu giữ các getter giá tương tự; đổi cache version khi cần và xác nhận một chu kỳ ghi → đọc bằng serializer production.

### F03 — P1: Duyệt/xóa review lỗi do kết quả tổng hợp dạng mảng lồng

**Bằng chứng: tái hiện với Spring Data repository thật trên H2.** [ProductReviewRepository](D:/khoaluantotnghiep/backend/src/main/java/com/core/beautyshop/modules/review/domain/ProductReviewRepository.java:14) trả `Object[]`, nhưng kết quả thực tế là mảng ngoài dài 1, phần tử đầu lại là `Object[]` chứa average/count.

[ProductReviewService](D:/khoaluantotnghiep/backend/src/main/java/com/core/beautyshop/modules/review/application/ProductReviewService.java:89) ép `summary[0]` sang Number và đọc `summary[1]`, gây `ClassCastException`. `moderate()` và `delete()` đều gọi đường này nên transaction không hoàn tất. Unit test hiện mock một mảng phẳng nên vẫn qua.

**Hướng xử lý:** dùng projection/DTO aggregate rõ kiểu hoặc đọc đúng cấu trúc, rồi kiểm tra duyệt/từ chối/xóa với repository thật và đối chiếu rating/count sản phẩm.

### F04 — P1: Đổi/xóa role chưa thu hồi quyền của JWT đang còn hạn

**Bằng chứng: xác định từ code.** [RoleServiceImpl](D:/khoaluantotnghiep/backend/src/main/java/com/core/beautyshop/modules/identity/application/service/RoleServiceImpl.java:62) đổi tên/xóa role nhưng không tăng token version của user liên quan và không thu hồi phiên. `JwtUtils:129`, `JwtAuthenticationFilter:46`, `AccessTokenRevocationService:18` lấy authority từ JWT và chỉ đối chiếu version.

**Kịch bản:** nhân viên đăng nhập với ORDER_STAFF; admin xóa/đổi role; token cũ vẫn được phép thao tác đơn hàng tới khi hết hạn. Gán role qua API user có tăng version, nhưng CRUD role toàn cục không có cùng cơ chế.

**Hướng xử lý:** bảo vệ mã role hệ thống; khi thay đổi quyền toàn cục phải thu hồi token/session của toàn bộ user liên quan, cập nhật cache sau commit và kiểm tra bằng token đã phát hành trước thao tác.

### F05 — P1: Nhập kho có nguy cơ mất cập nhật khi chạy đồng thời

**Bằng chứng: rủi ro đồng thời suy ra từ thứ tự đọc/khóa/ghi; chưa tái hiện trên MySQL.** [WarehouseStockServiceImpl](D:/khoaluantotnghiep/backend/src/main/java/com/core/beautyshop/modules/inventory/application/service/WarehouseStockServiceImpl.java:103) đọc tồn không khóa, tính số lượng tuyệt đối tại dòng 115, rồi mới khóa lô ở dòng 56 và ghi đè ở dòng 62.

**Kịch bản:** hai PO khác nhau cùng nhận vào lô tồn 10, nhập thêm 5 và 7; cùng đọc 10 và lưu 15/17 thay vì 22. Khóa phiếu mua chỉ bảo vệ nhận lại cùng PO. Entity đã được đọc trước khóa còn làm tăng nguy cơ sử dụng trạng thái cũ trong persistence context.

**Hướng xử lý:** khóa trước khi đọc/tính tồn, hoặc cộng nguyên tử với chính sách upsert cho lô mới. Kiểm tra đồng thời hai receipt cùng lô và receipt với thao tác giao đơn; đối chiếu cả tồn và ledger.

## 5. Các lỗi chi tiết theo nghiệp vụ

Các đường dẫn viết ngắn trong mục này nằm dưới `backend/src/main/java/com/core/beautyshop/`, trừ khi ghi rõ khác.

### Người dùng và phân quyền

**F06 — P2, xác định từ code: có thể mất admin hoạt động cuối cùng.** `modules/identity/application/service/RoleServiceImpl.java:70`, `UserServiceImpl.java:167`/`:180` không bảo vệ ROLE_ADMIN hoặc admin cuối cùng. Xóa role có FK CASCADE trong V1; tự bỏ quyền hoặc khóa admin cuối cùng làm mất lối quản trị. Cần invariant có ít nhất một ADMIN ACTIVE sau thao tác, kiểm tra trong transaction có khóa để chống hai thao tác đồng thời.

**F07 — P2, xác định từ code: refresh token đã thu hồi vẫn ép logout phiên mới.** `modules/identity/application/service/AuthServiceImpl.java:164` tìm session theo hash nhưng không kiểm tra revoked/expired trước khi tăng version toàn user. Login T1 → logout T1 → login lại A2 → logout lại bằng T1 vẫn thu hồi A2. Cần logout idempotent; token đã thu hồi không được tiếp tục gây tác động lên phiên mới.

**F08 — P2, xác định từ code: username/email có thể trùng chéo.** `AuthServiceImpl.java:84`, `RegisterRequest.java:20`, `shared/security/services/UserDetailsServiceImpl.java:22`. A có email `a@example.com`; B có thể đăng ký username đúng chuỗi đó với email khác. Truy vấn login username OR email khớp hai user trong khi repository trả Optional. Cần namespace đăng nhập không mơ hồ: cấm `@` trong username hoặc kiểm tra duy nhất chéo và xử lý rõ input.

### Đơn hàng và thanh toán

**F09 — P2, xác định từ code: ORDER_STAFF không đọc được detail đơn của khách.** `modules/order/api/AdminOrderController.java:28` cho phép staff, nhưng `application/service/OrderServiceImpl.java:163`/`:170` chỉ bypass chính chủ bằng `isAdmin()`. Nhân viên xem list được nhưng mở đơn khách khác/guest nhận 403. Cần use case đọc đơn quản trị có quyền phù hợp; API khách hàng vẫn phải kiểm chính chủ.

**F10 — P2, xác định từ code: đơn BANK tổng tiền 0 không hoàn tất được.** `OrderServiceImpl.java:128` cho giảm hết tiền nhưng vẫn PENDING; `modules/payment/application/service/PaymentWebhookProcessor.java:50` chỉ nhận tiền dương; xử lý/giao BANK cần PAID tại `OrderServiceImpl:265`. Đơn miễn phí chờ webhook không tồn tại rồi timeout. Cần nhánh hoàn tất nghĩa vụ thanh toán 0 đồng ngay khi checkout, với tác động kho/sự kiện đúng một lần.

**F11 — P2, xác định từ code: tiền trong QR bị cắt phần lẻ.** `modules/payment/application/strategy/BankPaymentStrategy.java:29` dùng `toBigInteger()`, còn `modules/order/application/facade/OrderFacadeImpl.java:159` so với BigDecimal đầy đủ. Tổng 100000.50 tạo QR 100000; chuyển đúng QR vẫn thiếu 0.50. Cần một quy tắc làm tròn tiền nhất quán trước khi lưu tổng, tạo QR và đối soát.

**F12 — P2, xác định từ code: giao COD ghi đè tiền chuyển khoản đã nhận.** `OrderFacadeImpl.java:135` nhận tiền cho cả COD; `OrderServiceImpl.java:270` khi DELIVERED lại đặt paidAmount bằng totalAmount. COD tổng 100000 nhận 150000 sẽ bị ghi lại thành 100000; khoản dư không còn phản ánh trong nghĩa vụ hoàn. Cần chính sách thanh toán kết hợp và ghi thu theo phần còn phải thu, bảo toàn số tiền thực nhận.

**F13 — P2, xác định từ code với dữ liệu cụ thể: job timeout bị chặn bởi 100 đơn PAID.** `modules/order/domain/OrderRepository.java:38` tìm BANK quá deadline nhưng không loại PAID. `application/service/OrderExpirationJob.java:19` luôn lấy 100 ID đầu; `OrderExpirationService.java:17` bỏ qua PAID. Đơn CONFIRMED nhận tiền vẫn có thể giữ CONFIRMED (`OrderFacadeImpl:168`). Nếu 100 đơn như vậy đứng đầu, đơn chưa thanh toán phía sau không được hủy/nhả tồn. Cần lọc đúng ở query và duyệt bằng cursor/batch không bị một nhóm bản ghi chặn.

### Kho và mua hàng

**F14 — P2, xác định từ code và mô hình được test: trừ tồn cách ly hai lần về mặt nghiệp vụ.** `modules/inventory/application/service/InventoryMovementService.java:20`/`:35` coi quarantinedQuantity nằm trong quantity. Nhưng `StockAllocationService.java:60` đưa hàng trả vào bucket cách ly riêng; `StockInspectionService.java:18` chỉ cộng vào quantity khi kiểm định đạt. Tồn bán được 7, cách ly 3, giữ chỗ 0 lại chỉ được chuyển 4. Cần thống nhất nghĩa tồn và cập nhật các test đang củng cố công thức sai.

**F15 — P2, xác định từ code: nhận/chuyển hàng vào lô đã xóa vẫn báo thành công.** `WarehouseStockServiceImpl.java:51`/`:103` tìm lô không lọc deleted và không phục hồi cờ; xóa lô đặt deleted tại dòng 137. `InventoryMovementService.java:40` có cùng vấn đề ở lô đích. Tồn/ledger tăng, PO có thể RECEIVED, nhưng bán hàng vẫn loại lô. Cần quy định khôi phục có kiểm soát hoặc từ chối rõ ràng.

**F16 — P2, xác định từ code: không nhập được tồn cho SKU nháp/ngừng bán.** `WarehouseStockServiceImpl.java:49` dùng catalog query cho mua hàng; `modules/catalog/domain/ProductVariantRepository.java:82` yêu cầu variant active và product ACTIVE. Luồng chuẩn tạo sản phẩm nháp → nhập tồn → phát hành bị chặn ở bước nhập. SKU bị ngừng bởi hết hạn cũng khó bổ sung hàng mới. Cần truy vấn nội bộ dành cho quản trị kho, kiểm tồn tại/chưa xóa, tách khỏi điều kiện đang bán.

**F17 — P2, xác định từ code: PO chấp nhận SKU không tồn tại tới bước duyệt.** `modules/procurement/application/ProcurementService.java:75` chỉ kiểm variantId không null; V25 dòng 47 không có FK SKU ở PO item. Phiếu được tạo/duyệt, tới receive mới lỗi catalog. Cần kiểm tham chiếu và trạng thái khi tạo/duyệt, thêm cơ chế sửa draft hoặc từ chối sớm dữ liệu không hợp lệ.

### Catalog và thuộc tính

**F18 — P2, xác định từ code: public detail chưa lọc trạng thái nhất quán.** `modules/catalog/application/service/ProductServiceImpl.java:42`/`:50` lọc soft delete nhưng chưa yêu cầu ACTIVE như list. `modules/spa/domain/BeautyServiceRepository.java:21`/`:25` detail còn không lọc active/deleted. Biết ID/slug có thể đọc sản phẩm nháp/ngừng bán hoặc dịch vụ đã xóa. Cần tách public detail và admin detail, áp dụng điều kiện công khai thống nhất.

**F19 — P2, tái hiện H2: đọc giá trị thuộc tính lỗi lazy loading.** `modules/catalog/application/service/ProductAttributeServiceImpl.java:79` không có transaction, repository không fetch definition; mapper ở dòng 143 đọc tên của LAZY definition. Probe gọi service với repository thật, dữ liệu có bản ghi và persistence context đã đóng nhận `LazyInitializationException`. Cấu hình ứng dụng tắt open-in-view. Cần transaction đọc, projection hoặc fetch đủ quan hệ trước mapping.

**F20 — P2, xác định từ code: datatype thuộc tính bị bỏ qua.** `ProductAttributeServiceImpl.java:49`/`:60` không lưu datatype dù request bắt buộc; mapper dòng 130 không trả datatype. Tạo NUMBER lưu mặc định STRING, response null; addValue luôn lưu valueString. Cần lưu/map datatype và validate giá trị theo kiểu; xử lý tương thích dữ liệu thuộc tính đã có trước khi đổi kiểu.

**F21 — P2, xác định từ code: gán category/tag bỏ ID không tồn tại mà vẫn thành công.** `ProductServiceImpl.java:127`/`:132`/`:250`/`:255` dùng findAllById nhưng không kiểm số lượng/đủ ID; category đã soft delete cũng có thể được gán. Request chứa một ID đúng và một ID sai chỉ lưu tập con. Cần kiểm toàn bộ ID và trạng thái trước khi thay mapping, trả lỗi rõ các tham chiếu sai.

### Dịch vụ, gói, vé và lịch Spa

**F22 — P2, xác định từ code: quản lý ca chưa ảnh hưởng booking và slot.** `modules/spa/application/service/impl/BeautyServiceServiceImpl.java:101`, `impl/AppointmentServiceImpl.java:94`/`:338`/`:402`/`:475` không truy vấn ca. Nhân viên chỉ có ca 13–17h vẫn được đặt/xác nhận lúc 9h; ngày nghỉ/ca hủy không được xét. `BeautyServiceServiceImpl:103` còn coi không có nhân viên đủ kỹ năng là slot trống. Cần một quy tắc khả dụng chung cho tìm slot, book, confirm, reschedule và thay đổi ca.

**F23 — P2, xác định từ code: bán gói chứa dịch vụ không còn phục vụ.** `modules/spa/application/service/AdminSpaCatalogService.java:48` xóa dịch vụ mà không xử lý gói; `impl/SpaTicketServiceImpl.java:110` không kiểm active/deleted của từng item. Khách thanh toán/có vé nhưng booking bị chặn ở `impl/AppointmentServiceImpl.java:469`. Cần chặn bán gói không hợp lệ, quản lý quan hệ lifecycle và có chính sách cho vé đã bán.

**F24 — P2, xác định từ code: cập nhật Spa không evict cache công khai.** `AdminSpaCatalogService.java:30`/`:48` và thao tác category không evict spa_services, trong khi `impl/BeautyServiceServiceImpl.java:37`/`:45`/`:53` cache list/detail/slug. Giá/tên/danh mục/slug cũ còn xuất hiện tối đa TTL 5 phút. Cần evict sau commit mọi thay đổi ảnh hưởng response công khai; F18 vẫn cần sửa vì hết cache không giải quyết điều kiện deleted.

**F25 — P2, xác định từ code: thời gian chuẩn bị âm làm lịch ngắn hơn dịch vụ.** `AdminSpaCatalogService.java:40`/`:114` không kiểm preparation >= 0; `impl/AppointmentServiceImpl.java:484` cộng preparation vào duration. Dịch vụ 60 phút với preparation -45 chỉ chiếm lịch 15 phút, cho phép lịch sau đến quá sớm. Cần validate thời lượng chuẩn bị và tổng thời gian ở mọi đường tạo/cập nhật.

**F26 — P2, xác định từ code: hạn gói âm thành vé không hết hạn.** `AdminSpaCatalogService.java:95` nhận validityDays âm; `PaidSpaTicketIssuer.java:53` chỉ tạo expiry nếu >0. Nhập -30 không lỗi mà sinh quyền vô thời hạn. Cần chỉ cho null theo chính sách không hết hạn; giá trị được nhập phải dương và có giới hạn hợp lý.

**F27 — P2, xác định từ code: lọc vé theo user bỏ qua status.** `AdminSpaTicketService.java:21` dùng nhánh userId trước status. Request userId cùng ACTIVE vẫn trả vé hết hạn/trạng thái khác. Cần query kết hợp tất cả điều kiện và kiểm tra totalElements tương ứng.

### Audit, thông báo, cấu hình và voucher

**F28 — P2, xác định từ code: audit bỏ sót các route quản trị ngoài /admin.** `shared/audit/application/aspect/AuditLogAspect.java:57` chỉ xét /api/v1/admin/ và /api/v1/roles. Mutation category/brand/product/attribute/tag ở route công khai dùng phân quyền admin nhưng không có AuditAction nên không có log. Force logout qua /users/admin và chatbot sync cũng bị bỏ qua; login/logout chưa được ghi dù mô tả audit nói có log bảo mật. Cần annotation hành động rõ hoặc cơ chế nhận diện đầy đủ, có test theo route và không log credential/token.

**F29 — P2, xác định từ code: email thất bại bị đánh dấu đã xử lý.** `modules/notification/application/service/NotificationServiceImpl.java:60` bắt lỗi SMTP rồi trả bình thường; `NotificationInbox.java:15` đã lưu receipt và transaction commit khi action hoàn tất. Kafka phát lại bị dedup, email không được gửi lại. Cần propagate lỗi để rollback/retry hoặc delivery job có trạng thái/retry riêng; kiểm tra tình huống SMTP lỗi rồi phục hồi.

**F30 — P2, xác định từ code: sửa ngưỡng cận hạn không ảnh hưởng cảnh báo.** `shared/config/api/AdminOperationalAlertController.java:28` hardcode 30 ngày trong SQL/title, dù V25 có inventory.expiry_warning_days và API sửa giá trị. Cần đọc config typed, validate giá trị và dùng chung ở cảnh báo liên quan. Các target dashboard được frontend đọc qua API config; không kết luận chúng hoàn toàn không có tác dụng.

**F31 — P2, xác định từ code: voucher thiếu kiểm tra ngưỡng tiền không âm.** `modules/promotion/application/VoucherService.java:118` chỉ kiểm discountValue, percentage, ngày và quota; không kiểm minOrderAmount/maxDiscountAmount âm, record request không có ràng buộc bổ sung và V24 cũng không chặn các trường này. maxDiscountAmount=-1 có thể tạo voucher hợp lệ theo API nhưng khi dùng discount bị chặn về 0 và vẫn ghi lượt sử dụng. Cần validate ngưỡng tiền, giới hạn độ dài và chính sách thay đổi quota đã sử dụng.

## 6. Những phần quản trị còn thiếu hoặc cần chốt quy tắc

Các mục sau không được cộng vào lỗi xác định ở trên. Cần đối chiếu tài liệu nghiệp vụ/UAT để quyết định triển khai.

| Mục | Khoảng trống hoặc định nghĩa hiện tại | Hướng hoàn thiện |
|---|---|---|
| Detail đơn | OrderMapper:20 chưa trả statusHistories/notes; item mapping:71 chưa trả productName/SKU snapshot mặc dù entity có lưu | DTO detail riêng phục vụ tra cứu và truy vết; giữ snapshot lịch sử |
| Vận chuyển/trả hàng | Phí ship tại OrderFactory:31 luôn 0; chưa có giao thất bại, đổi hàng, trả/hoàn từng phần hoặc xử lý dư tiền trên đơn thành công | Chốt các trường hợp cần có trong khóa luận/MVP; thiết kế trạng thái và ledger tương ứng |
| Mua hàng | Chưa sửa draft; chưa nhận từng phần, trả NCC và công nợ | Các mở rộng này đã được ghi ngoài baseline trong ADMIN_REVIEW_3_PHASES.md, không coi là lỗi bàn giao mặc định |
| User/RBAC | Danh sách chưa tìm kiếm/lọc; chưa đọc lịch sử khóa; thiếu đổi/reset mật khẩu và thu hồi một phiên. Role tùy ý không có ma trận permission tùy ý | Chốt vai trò cố định hay quyền cấu hình; bổ sung nghiệp vụ quản trị tài khoản cần thiết |
| Public catalog | Điều kiện ACTIVE phải có SKU bán được chưa được quy định đầy đủ; xóa brand/ingredient đang được product dùng chưa có chính sách | Chặn thao tác không hợp lệ hoặc giữ snapshot/lịch sử rõ ràng |
| CRM | Search LIMIT 100 không phân trang; notes không có phân trang; followUpAt chỉ lưu; ghi chú chống chỉ định không tự tham gia booking | Bổ sung phân trang, danh sách follow-up nếu cần; xác định rõ dữ liệu tham khảo và dữ liệu chặn nghiệp vụ |
| Facility | Có entity/repository/liên kết lịch hẹn nhưng chưa có quản trị phòng/giường và kiểm tra trùng tài nguyên | Chốt năng lực phục vụ ngoài nhân viên: phòng, giường, thiết bị, capacity |
| Dịch vụ Spa lẻ | Chưa thấy nối thanh toán lịch dịch vụ lẻ với order/payment như gói | Chốt thu trước, thu tại quầy hoặc chỉ dùng vé; mỗi lựa chọn cần luồng đối soát rõ |
| Hạn vé | Booking kiểm expiry với thời điểm hiện tại, chưa đối chiếu ngày sử dụng; reschedule chưa xét lại expiry | Chốt hạn áp dụng cho ngày đặt hay ngày thực hiện dịch vụ |
| Vé bị thu hồi | Admin gia hạn/bù buổi có thể đưa status về ACTIVE; booking vẫn kiểm paid order/expiry | Chốt trường hợp nào được khôi phục; không kết luận bypass hoàn tiền khi chưa kiểm đủ điều kiện |
| Ngừng nhân viên/kỹ năng | Chưa xử lý lịch tương lai đã xác nhận khi ngừng staff/xóa skill | Chặn, cảnh báo hoặc tái phân công theo chính sách |
| Voucher hủy đơn | Lượt dùng được ghi lúc checkout; chưa có chính sách trả quota khi hủy/timeout/hoàn | Chốt quota là lượt đặt hay lượt mua hoàn tất; kiểm tra idempotency khi hoàn quota |
| Outbox/DLT | Event FAILED chặn các event sau cùng aggregate; chưa thấy API retry/skip/replay | Cần công cụ vận hành có audit, trạng thái và quyền riêng |
| Thông báo | Status/cancel hiện chỉ log ở NotificationServiceImpl:95/:100 | Chốt email/push/in-app và theo dõi trạng thái gửi nếu đây là yêu cầu thực |

### Số liệu tổng thể cần thống nhất

- **Dashboard doanh thu:** `AdminDashboardService.java:24`/`:49` lấy paidAmount của các đơn hiện có paymentStatus=PAID và nhóm theo ngày tạo đơn. Đơn tạo 30/09 nhưng trả tiền 02/10 vẫn thuộc 30/09. Khi chuyển REFUND_PENDING, tiền bị loại khỏi KPI trước khi xác nhận trả thực tế. Đây là tổng giá trị một tập đơn theo trạng thái hiện tại, chưa phải dòng tiền thực thu theo ngày thanh toán như nhãn frontend thể hiện. Cần tách doanh số, tiền nhận, tiền hoàn và công nợ; chốt dùng ngày tạo, ngày thu hay ngày giao.
- **Đối soát payment:** `AdminPaymentController.java:55` chỉ cộng SUCCESS/PARTIALLY_PAID; không bao gồm tiền chuyển tới đơn đã hủy/không khớp và không trừ refund. Nếu gọi số đó là toàn bộ tiền ngân hàng thực nhận thì thiếu. Cần thống kê theo ledger và trình bày các bucket riêng.
- **Average order value:** overview lấy AVG(totalAmount) của đơn PAID trong khi doanh thu SUM(paidAmount); tiền trả dư làm AOV khác revenue/paidOrders, trái mô tả ở giao diện. Cần một định nghĩa nhất quán.
- **Spa occupancy:** tử số gồm mọi appointment trừ CANCELLED, kể cả PENDING/NO_SHOW; mẫu số là ca SCHEDULED. Đây không tương đương giờ đã thực hiện dịch vụ. Đang chặn tỷ lệ ở 100% nên không thể thấy vượt năng lực. Cần phân biệt tải đã đặt, tải đã phục vụ và vượt công suất; kiểm tra from/to hợp lệ.
- **Khách hàng/tồn thấp:** activeCustomers đếm mọi user ACTIVE, gồm nhân viên/admin; lowStock/alert đếm dòng lô dù nhãn là SKU. Cần tên KPI đúng đối tượng hoặc query đúng định nghĩa.

### Các điểm P3 và khả năng vận hành

| Điểm | Bằng chứng | Hướng xử lý |
|---|---|---|
| Xóa ca nhưng vẫn hiện trong list | AdminStaffService:94 soft delete; StaffScheduleRepository:10 thiếu isDeletedFalse | Lọc deleted trong query, giữ lịch sử ở chế độ riêng nếu cần |
| Xóa config rồi tạo lại cùng key gặp 409 | AdminSystemConfigController:48 chỉ kiểm key chưa xóa; :76 soft delete; SystemConfig unique toàn bảng | Restore bản ghi cũ hoặc quy định không tái sử dụng và trả lỗi rõ |
| Summary payment tải toàn bảng | AdminPaymentController:54 findAll rồi tính trong Java | Aggregate tại DB; thêm khoảng thời gian theo nhu cầu đối soát |
| Validation các command admin còn mỏng | Voucher/CRM/Spa dùng record thiếu nhiều giới hạn; DB mới chặn độ dài ở bước ghi | Validate enum, độ dài, số tiền/thời gian ở API/service; tránh báo thành công với dữ liệu bị bỏ qua |
| Audit/payment export ở frontend | Frontend có xuất CSV nhưng chưa xác minh bao phủ toàn bộ trang dữ liệu | UAT việc export có đầy đủ kết quả và quyền truy cập đúng |

## 7. Rủi ro cần kiểm chứng thêm

1. **P1 có điều kiện — JWT secret mặc định công khai.** `application.yaml:144`, `shared/security/jwt/JwtUtils.java:28`, `docker-compose.yml:90` có fallback cố định. Nếu không đặt JWT_SECRET riêng, người biết source có thể ký token tự gán authority ADMIN cho user có version hợp lệ. Chưa kiểm tra secret của môi trường hiện tại. Cần fail startup khi thiếu/default, tách secret test và kiểm thử cấu hình deploy.
2. **Lô batchCode null và tạo đồng thời.** Khóa unique gồm batchCode nullable; hai request tạo cùng kho/SKU khi lô chưa có có thể tạo nhiều hàng null, về sau query Optional gặp nhiều kết quả. Cần chuẩn hóa batch key và kiểm tra upsert trên MySQL.
3. **Biến thể default.** ProductVariantServiceImpl đọc variant trước khi khóa product; request chờ khóa có thể giữ trạng thái cũ và ghi lại default sau khi request khác đổi default. Cần test hai transaction và khóa trước khi đọc entity sẽ ghi hoặc refresh đúng lúc.
4. **Chu trình danh mục/ca/kỹ năng khi đồng thời.** Các check-then-write tuần tự chưa đủ chứng minh an toàn cho hai request cùng sửa quan hệ. Cần lock/constraint và test phù hợp, không coi unit test tuần tự là kiểm thử race.
5. **GET gói Spa trong transaction readOnly lấy khóa ghi.** AdminSpaCatalogService:80/:82 gọi repository PESSIMISTIC_WRITE. Cần kiểm trên MySQL/ConnectorJ thật; GET nên dùng query đọc thường để tránh khóa không cần thiết và khả năng lỗi transaction read-only.
6. **SVG upload có nội dung script.** MediaStorageService:30/:107 chấp nhận SVG dựa trên MIME/đuôi và không sanitize nội dung. Upload được phục vụ public; mở trực tiếp cùng origin có thể thực thi script. Chưa chạy exploit browser trong lần review. Cần sanitize/chuyển bitmap hoặc origin riêng, kiểm luồng hiển thị và header thực tế.

## 8. Kiểm thử đã thực hiện và giới hạn

Lệnh chạy tại `D:\khoaluantotnghiep\backend`: `mvnw.cmd test -B`. Java 21; Maven báo thời gian 1 phút 26 giây, exit code 1.

| Kết quả | Số lượng |
|---|---:|
| Tổng | 138 |
| Qua | 128 |
| Failure | 2 |
| Error | 1 |
| Skipped | 7 |

Các test không đạt:

| Test | Nguyên nhân xác minh | Kết luận |
|---|---|---|
| CacheConfigTest.productPageCanRoundTripAsJson | JSON có price nhưng DTO không nhận lại trường này | Lỗi implementation ở F02 |
| ComprehensiveSwaggerAndBearerApiTest.testAllSwaggerGroups | Test gọi group cũ “5. Kho hàng & Tồn kho”, config đã đổi thành “5. Kho hàng & Mua hàng” | Test/hợp đồng tên group chưa cập nhật; không kết luận mọi Swagger group đang hỏng |
| AdminMediaControllerTest.uploadEndpointReturnsCreatedWithUrlAndPublicUrl | API trả 201 đúng, nhưng test đòi $.success trong khi ApiResponse dùng status/message/data | Assertion lệch hợp đồng chung; không kết luận upload thất bại |

7 test MySqlCheckoutIntegrityTest bị bỏ qua vì Testcontainers không tìm được Docker hoạt động. H2 test profile dùng Hibernate create-drop và tắt Flyway; do đó lần chạy này không kiểm chứng V25–V28, locking/unique của MySQL hay toàn bộ integration Redis/Kafka thật.

Hai kiểm tra bổ sung đặt ở `target/review-probes/AdminReviewProbe.java`, chạy bằng classpath Maven và H2 riêng `review_probe`:

```text
PROBE aggregate outerLength=1 firstType=[Ljava.lang.Object;
PROBE service extraction fails=java.lang.ClassCastException
PROBE attribute values fails=org.hibernate.LazyInitializationException
```

Probe tái hiện cấu trúc kết quả repository và đoạn đọc aggregate của review, cùng service GET values với persistence context đã đóng. Đây là kiểm tra độc lập, không được cộng vào 138 test và không phải kiểm thử endpoint HTTP end-to-end.

Các kiểm tra hiện có qua gồm kiến trúc module, context Spring, auth/JWT, checkout đồng thời trên H2, trạng thái đơn, allocation/FEFO, webhook trùng/đến muộn, refund, vé Spa, listener và outbox claim. Chưa có số liệu coverage để quy đổi thành tỷ lệ backend được kiểm chứng.

Tài liệu kết quả:

- [Log regression](D:/khoaluantotnghiep/backend/target/admin-review-2026-10-02-tests.log).
- [Log probe](D:/khoaluantotnghiep/backend/target/admin-review-2026-10-02-probes.log).
- [Surefire reports](D:/khoaluantotnghiep/backend/target/surefire-reports).

Các file trong target có thể bị mất khi chạy Maven clean; báo cáo này lưu lại các kết luận và số liệu chính.

## 9. Thứ tự xử lý đề xuất và tiêu chí nghiệm thu

1. **Bảo toàn dữ liệu:** xử lý F01 trước mọi lần áp dụng migration lên DB có dữ liệu; kiểm thử nâng cấp từ bản sao có đơn, PO, stock allocation, review và thuộc tính. Sau nâng cấp, số lượng/ID/tham chiếu/lịch sử phải giữ nguyên theo thiết kế.
2. **Đưa các luồng đang lỗi về hoạt động:** F02, F03, F19; cập nhật hai assertion lỗi thời và chạy regression. Kiểm tra cache bằng serializer production và moderation bằng repository thật.
3. **Khóa tính đúng đắn:** F04–F08, F05, F09–F17; test từng vai trò thực, hai transaction receipt, đơn 0 đồng, tiền lẻ, COD có chuyển khoản, timeout batch và lô đã xóa.
4. **Hoàn thiện Spa:** F22–F27, F18; test ngoài ca/ngày nghỉ/không có nhân viên, gói chứa dịch vụ ngừng, thời gian/hạn âm, cache và bộ lọc kết hợp.
5. **Hoàn thiện vận hành:** F28–F31, audit đầy đủ, SMTP retry, config có tác dụng, KPI có định nghĩa rõ và recovery outbox/DLT.
6. **Chốt phần thiếu theo phạm vi khóa luận:** detail đơn, CRM follow-up, facility, thanh toán dịch vụ lẻ, draft PO, mật khẩu/phiên và các chính sách hoàn quota/vé. Tách mở rộng ngoài baseline khỏi lỗi cần sửa.

Điều kiện để kết luận backend quản trị đã đúng logic: regression không lỗi, các kịch bản trên có kết quả kiểm chứng, migration MySQL bảo toàn dữ liệu, phân quyền đúng ở cả list/detail/mutation, và số liệu quản trị đối chiếu được với giao dịch thực tế. Hiện tại chưa đủ các điều kiện này.
