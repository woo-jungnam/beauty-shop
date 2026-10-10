# Danh mục endpoint API

Sinh từ OpenAPI đã kiểm chứng lúc 2026-10-02T18:07:31+07:00. **253 operations trên 179 đường dẫn.**

Nguồn chi tiết: [OpenAPI JSON](openapi.json), [hướng dẫn Swagger](API_GUIDE.md). Bảng ghi HTTP thành công; lỗi và schema request/response tra trong Swagger. Role ở guard chưa thay điều kiện ownership/phân công/state của service.

## Xác thực, tài khoản và vai trò — 27 operations

| Method | Path | Hành động | Xác thực / guard | HTTP thành công |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/admin/users` | Lấy danh sách người dùng (ADMIN) | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/users/{id}` | Xem thông tin chi tiết người dùng theo ID | hasRole('ADMIN') | 200 |
| POST | `/api/v1/admin/users/{id}/force-logout` | Buộc đăng xuất toàn bộ phiên của người dùng | hasRole('ADMIN') | 200 |
| PUT | `/api/v1/admin/users/{id}/roles` | Thay thế toàn bộ vai trò của người dùng | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/users/{id}/sessions` | Xem lịch sử phiên đăng nhập của người dùng | hasRole('ADMIN') | 200 |
| DELETE | `/api/v1/admin/users/{id}/sessions/{sessionId}` | Thu hồi một họ phiên của đúng người dùng | hasRole('ADMIN') | 200 |
| PUT | `/api/v1/admin/users/{id}/status` | Cập nhật trạng thái tài khoản (ACTIVE, BLOCKED, SUSPENDED) | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/users/{id}/status-history` | Xem lịch sử thay đổi trạng thái tài khoản | hasRole('ADMIN') | 200 |
| POST | `/api/v1/auth/login` | Đăng nhập tài khoản và nhận cặp JWT | Công khai | 200 |
| POST | `/api/v1/auth/logout` | Đăng xuất một họ phiên | Công khai | 200 |
| GET | `/api/v1/auth/profile` | Lấy thông tin hồ sơ người dùng đang đăng nhập | isAuthenticated() | 200 |
| POST | `/api/v1/auth/refresh` | Luân chuyển refresh token và cấp cặp token mới | Công khai | 200 |
| POST | `/api/v1/auth/register` | Đăng ký tài khoản khách hàng mới | Công khai | 201 |
| GET | `/api/v1/roles` | Lấy danh sách tất cả vai trò (Admin) | hasRole('ADMIN') | 200 |
| POST | `/api/v1/roles` | Tạo vai trò mới (ADMIN) | hasRole('ADMIN') | 201 |
| DELETE | `/api/v1/roles/{id}` | Xóa vai trò tùy chỉnh (ADMIN) | hasRole('ADMIN') | 200 |
| GET | `/api/v1/roles/{id}` | Lấy thông tin vai trò theo ID (Admin) | hasRole('ADMIN') | 200 |
| PUT | `/api/v1/roles/{id}` | Cập nhật vai trò (ADMIN) | hasRole('ADMIN') | 200 |
| GET | `/api/v1/test/admin` | Kiểm tra truy cập quyền quản trị (Chỉ Admin) | hasRole('ADMIN') | 200 |
| GET | `/api/v1/test/public` | Kiểm tra truy cập công khai (Public) | Công khai | 200 |
| GET | `/api/v1/test/user` | Kiểm tra truy cập ROLE_USER hoặc ROLE_ADMIN | hasAnyRole('USER', 'ADMIN') | 200 |
| GET | `/api/v1/users/admin/all` | Lấy danh sách người dùng qua tuyến tương thích (ADMIN) | hasRole('ADMIN') | 200 |
| GET | `/api/v1/users/admin/{id}` | Xem thông tin người dùng theo ID (Admin) | hasRole('ADMIN') | 200 |
| POST | `/api/v1/users/admin/{id}/force-logout` | Buộc đăng xuất người dùng trên tất cả thiết bị (ADMIN) | hasRole('ADMIN') | 200 |
| GET | `/api/v1/users/me` | Lấy thông tin cá nhân hiện tại | isAuthenticated() | 200 |
| GET | `/api/v1/users/profile` | Lấy thông tin cá nhân hiện tại | isAuthenticated() | 200 |
| PUT | `/api/v1/users/profile` | Cập nhật thông tin cá nhân | isAuthenticated() | 200 |

## Sản phẩm và danh mục — 61 operations

| Method | Path | Hành động | Xác thực / guard | HTTP thành công |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/admin/ingredients` | Lấy danh sách hoạt chất (phân trang) | hasAnyRole('ADMIN', 'CATALOG_STAFF') | 200 |
| POST | `/api/v1/admin/ingredients` | Tạo hoạt chất mỹ phẩm mới | hasAnyRole('ADMIN', 'CATALOG_STAFF') | 201 |
| GET | `/api/v1/admin/ingredients/products/{productId}` | Lấy danh sách hoạt chất trong sản phẩm | hasAnyRole('ADMIN', 'CATALOG_STAFF') | 200 |
| PUT | `/api/v1/admin/ingredients/products/{productId}` | Thay toàn bộ danh sách hoạt chất cho sản phẩm | hasAnyRole('ADMIN', 'CATALOG_STAFF') | 200 |
| DELETE | `/api/v1/admin/ingredients/{id}` | Xóa hoạt chất mỹ phẩm | hasAnyRole('ADMIN', 'CATALOG_STAFF') | 200 |
| GET | `/api/v1/admin/ingredients/{id}` | Xem chi tiết hoạt chất theo ID | hasAnyRole('ADMIN', 'CATALOG_STAFF') | 200 |
| PUT | `/api/v1/admin/ingredients/{id}` | Cập nhật thông tin hoạt chất | hasAnyRole('ADMIN', 'CATALOG_STAFF') | 200 |
| POST | `/api/v1/admin/media/upload` | Tải tệp hình ảnh lên máy chủ | hasAnyRole('ADMIN', 'CATALOG_STAFF') | 201 |
| GET | `/api/v1/admin/products` | Lấy danh sách sản phẩm quản trị (phân trang) | hasAnyRole('ADMIN', 'CATALOG_STAFF') | 200 |
| POST | `/api/v1/admin/products` | Tạo sản phẩm mới | hasAnyRole('ADMIN', 'CATALOG_STAFF') | 201 |
| DELETE | `/api/v1/admin/products/{id}` | Xóa sản phẩm (Soft delete) | hasAnyRole('ADMIN', 'CATALOG_STAFF') | 200 |
| GET | `/api/v1/admin/products/{id}` | Xem chi tiết sản phẩm theo ID | hasAnyRole('ADMIN', 'CATALOG_STAFF') | 200 |
| PUT | `/api/v1/admin/products/{id}` | Cập nhật thông tin sản phẩm | hasAnyRole('ADMIN', 'CATALOG_STAFF') | 200 |
| POST | `/api/v1/admin/products/{id}/images` | Thêm ảnh vào thư viện sản phẩm | hasAnyRole('ADMIN', 'CATALOG_STAFF') | 201 |
| DELETE | `/api/v1/admin/products/{id}/images/{imageId}` | Xóa ảnh khỏi thư viện sản phẩm | hasAnyRole('ADMIN', 'CATALOG_STAFF') | 200 |
| POST | `/api/v1/admin/products/{id}/variants` | Thêm biến thể SKU mới cho sản phẩm | hasAnyRole('ADMIN', 'CATALOG_STAFF') | 201 |
| DELETE | `/api/v1/admin/products/{id}/variants/{variantId}` | Xóa biến thể SKU | hasAnyRole('ADMIN', 'CATALOG_STAFF') | 200 |
| PUT | `/api/v1/admin/products/{id}/variants/{variantId}` | Cập nhật biến thể SKU | hasAnyRole('ADMIN', 'CATALOG_STAFF') | 200 |
| GET | `/api/v1/attributes` | Lấy tất cả định nghĩa thuộc tính | Công khai | 200 |
| POST | `/api/v1/attributes` | Tạo định nghĩa thuộc tính mới (ADMIN / CATALOG_STAFF) | hasAnyRole('ADMIN', 'CATALOG_STAFF') | 201 |
| DELETE | `/api/v1/attributes/values/{valueId}` | Xóa giá trị thuộc tính (ADMIN / CATALOG_STAFF) | hasAnyRole('ADMIN', 'CATALOG_STAFF') | 200 |
| DELETE | `/api/v1/attributes/{id}` | Xóa định nghĩa thuộc tính (ADMIN / CATALOG_STAFF) | hasAnyRole('ADMIN', 'CATALOG_STAFF') | 200 |
| GET | `/api/v1/attributes/{id}` | Lấy định nghĩa thuộc tính theo ID | Công khai | 200 |
| PUT | `/api/v1/attributes/{id}` | Cập nhật định nghĩa thuộc tính (ADMIN / CATALOG_STAFF) | hasAnyRole('ADMIN', 'CATALOG_STAFF') | 200 |
| GET | `/api/v1/attributes/{id}/values` | Lấy danh sách giá trị theo định nghĩa thuộc tính | Công khai | 200 |
| POST | `/api/v1/attributes/{id}/values` | Thêm giá trị thuộc tính (ADMIN / CATALOG_STAFF) | hasAnyRole('ADMIN', 'CATALOG_STAFF') | 201 |
| GET | `/api/v1/brands` | Lấy danh sách tất cả thương hiệu (phân trang) | Công khai | 200 |
| POST | `/api/v1/brands` | Tạo thương hiệu mới (ADMIN / CATALOG_STAFF) | hasAnyRole('ADMIN','CATALOG_STAFF') | 201 |
| GET | `/api/v1/brands/slug/{slug}` | Xem chi tiết thương hiệu theo Slug | Công khai | 200 |
| DELETE | `/api/v1/brands/{id}` | Xóa thương hiệu (ADMIN / CATALOG_STAFF) | hasAnyRole('ADMIN','CATALOG_STAFF') | 200 |
| GET | `/api/v1/brands/{id}` | Xem chi tiết thương hiệu theo ID | Công khai | 200 |
| PUT | `/api/v1/brands/{id}` | Cập nhật thông tin thương hiệu (ADMIN / CATALOG_STAFF) | hasAnyRole('ADMIN','CATALOG_STAFF') | 200 |
| GET | `/api/v1/categories` | Lấy danh sách tất cả danh mục | Công khai | 200 |
| POST | `/api/v1/categories` | Tạo danh mục mới (ADMIN / CATALOG_STAFF) | hasAnyRole('ADMIN','CATALOG_STAFF') | 201 |
| GET | `/api/v1/categories/root` | Lấy danh sách các danh mục gốc | Công khai | 200 |
| GET | `/api/v1/categories/slug/{slug}` | Xem chi tiết danh mục theo Slug | Công khai | 200 |
| DELETE | `/api/v1/categories/{id}` | Xóa danh mục (ADMIN / CATALOG_STAFF) | hasAnyRole('ADMIN','CATALOG_STAFF') | 200 |
| GET | `/api/v1/categories/{id}` | Xem chi tiết danh mục theo ID | Công khai | 200 |
| PUT | `/api/v1/categories/{id}` | Cập nhật danh mục (ADMIN / CATALOG_STAFF) | hasAnyRole('ADMIN','CATALOG_STAFF') | 200 |
| GET | `/api/v1/products` | Lấy danh sách tất cả sản phẩm (phân trang) | Công khai | 200 |
| POST | `/api/v1/products` | Tạo sản phẩm mới (Admin) | hasRole('ADMIN') | 201 |
| GET | `/api/v1/products/brand/{brandId}` | Lấy danh sách sản phẩm theo thương hiệu | Công khai | 200 |
| GET | `/api/v1/products/category/{categoryId}` | Lấy danh sách sản phẩm theo danh mục | Công khai | 200 |
| GET | `/api/v1/products/featured` | Lấy sản phẩm nổi bật | Công khai | 200 |
| GET | `/api/v1/products/search` | Tìm kiếm và lọc sản phẩm | Công khai | 200 |
| GET | `/api/v1/products/slug/{slug}` | Xem chi tiết sản phẩm theo Slug | Công khai | 200 |
| DELETE | `/api/v1/products/{id}` | Xóa sản phẩm (Admin) | hasRole('ADMIN') | 200 |
| GET | `/api/v1/products/{id}` | Xem chi tiết sản phẩm theo ID | Công khai | 200 |
| PUT | `/api/v1/products/{id}` | Cập nhật thông tin sản phẩm (Admin) | hasRole('ADMIN') | 200 |
| GET | `/api/v1/products/{id}/images` | Lấy danh sách hình ảnh của sản phẩm | Công khai | 200 |
| POST | `/api/v1/products/{id}/images` | Thêm hình ảnh cho sản phẩm (Admin) | hasRole('ADMIN') | 201 |
| DELETE | `/api/v1/products/{id}/images/{imageId}` | Xóa hình ảnh của sản phẩm (Admin) | hasRole('ADMIN') | 200 |
| GET | `/api/v1/products/{id}/variants` | Lấy danh sách biến thể của sản phẩm | Công khai | 200 |
| POST | `/api/v1/products/{id}/variants` | Thêm biến thể mới cho sản phẩm (Admin) | hasRole('ADMIN') | 201 |
| DELETE | `/api/v1/products/{id}/variants/{variantId}` | Xóa biến thể sản phẩm (Admin) | hasRole('ADMIN') | 200 |
| PUT | `/api/v1/products/{id}/variants/{variantId}` | Cập nhật biến thể sản phẩm (Admin) | hasRole('ADMIN') | 200 |
| GET | `/api/v1/tags` | Lấy danh sách tất cả thẻ | Công khai | 200 |
| POST | `/api/v1/tags` | Tạo thẻ sản phẩm mới (Admin) | hasRole('ADMIN') | 201 |
| DELETE | `/api/v1/tags/{id}` | Xóa thẻ sản phẩm (Admin) | hasRole('ADMIN') | 200 |
| GET | `/api/v1/tags/{id}` | Lấy thông tin thẻ theo ID | Công khai | 200 |
| PUT | `/api/v1/tags/{id}` | Cập nhật thẻ sản phẩm (Admin) | hasRole('ADMIN') | 200 |

## Giỏ hàng — 6 operations

| Method | Path | Hành động | Xác thực / guard | HTTP thành công |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/cart` | Lấy giỏ hàng hiện tại | JWT tùy chọn / phiên khách | 200 |
| POST | `/api/v1/cart/add` | Thêm sản phẩm vào giỏ hàng | JWT tùy chọn / phiên khách | 200 |
| DELETE | `/api/v1/cart/clear` | Xóa toàn bộ giỏ hàng | JWT tùy chọn / phiên khách | 200 |
| DELETE | `/api/v1/cart/items/{itemId}` | Xóa sản phẩm khỏi giỏ hàng | JWT tùy chọn / phiên khách | 200 |
| PUT | `/api/v1/cart/items/{itemId}` | Cập nhật số lượng sản phẩm trong giỏ hàng | JWT tùy chọn / phiên khách | 200 |
| POST | `/api/v1/cart/merge` | Gộp giỏ khách vãng lai vào tài khoản | isAuthenticated() | 200 |

## Đơn hàng và hoàn tiền — 11 operations

| Method | Path | Hành động | Xác thực / guard | HTTP thành công |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/admin/orders` | Tìm đơn hàng quản trị | hasAnyRole('ADMIN', 'ORDER_STAFF') | 200 |
| GET | `/api/v1/admin/orders/{id}` | Xem chi tiết đơn cho ADMIN/ORDER_STAFF | hasAnyRole('ADMIN', 'ORDER_STAFF') | 200 |
| POST | `/api/v1/admin/orders/{id}/refund-confirmation` | Ghi nhận xác nhận đã hoàn tiền thủ công | hasRole('ADMIN') | 200 |
| PUT | `/api/v1/admin/orders/{id}/status` | Điều phối trạng thái đơn cho ADMIN/ORDER_STAFF | hasAnyRole('ADMIN', 'ORDER_STAFF') | 200 |
| GET | `/api/v1/orders` | Lấy tất cả đơn hàng (Admin) | hasRole('ADMIN') | 200 |
| POST | `/api/v1/orders/checkout` | Thanh toán & tạo đơn hàng mới | JWT tùy chọn / phiên khách | 201 |
| GET | `/api/v1/orders/my-orders` | Xem danh sách đơn hàng của tôi | isAuthenticated() | 200 |
| GET | `/api/v1/orders/user/{userId}` | Xem đơn hàng theo User ID (Admin hoặc chính chủ) | isAuthenticated() | 200 |
| GET | `/api/v1/orders/{id}` | Xem chi tiết đơn hàng | JWT tùy chọn / phiên khách | 200 |
| DELETE | `/api/v1/orders/{id}/cancel` | Hủy đơn hàng | isAuthenticated() | 200 |
| PUT | `/api/v1/orders/{id}/status` | Cập nhật trạng thái đơn hàng (Admin) | hasRole('ADMIN') | 200 |

## Thanh toán và webhook — 4 operations

| Method | Path | Hành động | Xác thực / guard | HTTP thành công |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/admin/payments` | Lấy sổ giao dịch thanh toán | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/payments/summary` | Tổng hợp thực thu và hoàn tiền | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/payments/{id}` | Xem chi tiết giao dịch thanh toán theo ID | hasRole('ADMIN') | 200 |
| POST | `/api/v1/payment/sepay-webhook` | Tiếp nhận webhook SePay | API key SePay | 200 |

## Dịch vụ, liệu trình và lịch hẹn Spa — 79 operations

| Method | Path | Hành động | Xác thực / guard | HTTP thành công |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/admin/spa/categories` | Lấy danh sách danh mục Spa (phân trang) | hasRole('ADMIN') | 200 |
| POST | `/api/v1/admin/spa/categories` | Tạo danh mục Spa mới | hasRole('ADMIN') | 201 |
| DELETE | `/api/v1/admin/spa/categories/{id}` | Xóa mềm danh mục Spa | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/spa/categories/{id}` | Xem chi tiết danh mục Spa theo ID | hasRole('ADMIN') | 200 |
| PUT | `/api/v1/admin/spa/categories/{id}` | Cập nhật danh mục Spa | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/spa/facilities` | Danh sách tài nguyên Spa chưa xóa | hasRole('ADMIN') | 200 |
| POST | `/api/v1/admin/spa/facilities` | Tạo giường, phòng hoặc thiết bị | hasRole('ADMIN') | 201 |
| DELETE | `/api/v1/admin/spa/facilities/{id}` | Xóa mềm tài nguyên Spa | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/spa/facilities/{id}` | Xem chi tiết tài nguyên Spa | hasRole('ADMIN') | 200 |
| PUT | `/api/v1/admin/spa/facilities/{id}` | Cập nhật tài nguyên Spa | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/spa/facilities/{id}/blocks` | Xem các khoảng bảo trì của tài nguyên | hasRole('ADMIN') | 200 |
| POST | `/api/v1/admin/spa/facilities/{id}/blocks` | Tạo khoảng ngừng phục vụ tài nguyên | hasRole('ADMIN') | 201 |
| DELETE | `/api/v1/admin/spa/facilities/{id}/blocks/{blockId}` | Xóa mềm khoảng bảo trì | hasRole('ADMIN') | 200 |
| PUT | `/api/v1/admin/spa/facilities/{id}/blocks/{blockId}` | Đổi khoảng bảo trì tài nguyên | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/spa/packages` | Lấy danh sách gói liệu trình Spa | hasRole('ADMIN') | 200 |
| POST | `/api/v1/admin/spa/packages` | Tạo gói liệu trình Spa mới | hasRole('ADMIN') | 201 |
| DELETE | `/api/v1/admin/spa/packages/{id}` | Xóa mềm gói liệu trình Spa | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/spa/packages/{id}` | Xem chi tiết gói liệu trình Spa theo ID | hasRole('ADMIN') | 200 |
| PUT | `/api/v1/admin/spa/packages/{id}` | Cập nhật gói liệu trình Spa | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/spa/preparation/services/{id}` | Xem yêu cầu form/cảnh báo của dịch vụ | hasRole('ADMIN') | 200 |
| PUT | `/api/v1/admin/spa/preparation/services/{id}` | Thay chính sách chuẩn bị cho booking mới | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/spa/preparation/templates` | Phân trang các template form hiện hành | hasRole('ADMIN') | 200 |
| POST | `/api/v1/admin/spa/preparation/templates` | Tạo template và phiên bản form đầu tiên | hasRole('ADMIN') | 201 |
| GET | `/api/v1/admin/spa/preparation/templates/{id}/versions` | Xem lịch sử phiên bản của template | hasRole('ADMIN') | 200 |
| POST | `/api/v1/admin/spa/preparation/templates/{id}/versions` | Tạo phiên bản form mới | hasRole('ADMIN') | 201 |
| GET | `/api/v1/admin/spa/services` | Lấy danh sách dịch vụ Spa (phân trang) | hasRole('ADMIN') | 200 |
| POST | `/api/v1/admin/spa/services` | Tạo dịch vụ Spa mới | hasRole('ADMIN') | 201 |
| DELETE | `/api/v1/admin/spa/services/{id}` | Xóa mềm dịch vụ Spa | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/spa/services/{id}` | Xem chi tiết dịch vụ Spa theo ID | hasRole('ADMIN') | 200 |
| PUT | `/api/v1/admin/spa/services/{id}` | Cập nhật dịch vụ Spa | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/spa/services/{id}/resource-requirements` | Xem yêu cầu tài nguyên hiện tại của dịch vụ | hasRole('ADMIN') | 200 |
| PUT | `/api/v1/admin/spa/services/{id}/resource-requirements` | Thay toàn bộ yêu cầu tài nguyên dịch vụ | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/spa/tickets` | Tìm kiếm vé Spa theo khách/trạng thái | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/spa/tickets/{id}` | Xem chi tiết vé Spa của khách | hasRole('ADMIN') | 200 |
| PUT | `/api/v1/admin/spa/tickets/{id}/compensate` | Bù lượt cho một dịch vụ trong vé | hasRole('ADMIN') | 200 |
| PUT | `/api/v1/admin/spa/tickets/{id}/extend` | Gia hạn vé Spa với bằng chứng điều chỉnh | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/staff` | Lấy danh sách chuyên viên Spa | hasRole('ADMIN') | 200 |
| POST | `/api/v1/admin/staff` | Thêm hồ sơ chuyên viên Spa | hasRole('ADMIN') | 201 |
| DELETE | `/api/v1/admin/staff/{id}` | Xóa mềm chuyên viên Spa | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/staff/{id}` | Xem chi tiết chuyên viên Spa theo ID | hasRole('ADMIN') | 200 |
| PUT | `/api/v1/admin/staff/{id}` | Cập nhật hồ sơ chuyên viên Spa | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/staff/{id}/schedules` | Phân trang ca làm việc của chuyên viên | hasRole('ADMIN') | 200 |
| POST | `/api/v1/admin/staff/{id}/schedules` | Thêm ca làm việc cho chuyên viên | hasRole('ADMIN') | 201 |
| DELETE | `/api/v1/admin/staff/{id}/schedules/{scheduleId}` | Xóa mềm ca làm việc của chuyên viên | hasRole('ADMIN') | 200 |
| PUT | `/api/v1/admin/staff/{id}/schedules/{scheduleId}` | Cập nhật ca làm việc của chuyên viên | hasRole('ADMIN') | 200 |
| PUT | `/api/v1/admin/staff/{id}/skills` | Gán hoặc cập nhật kỹ năng dịch vụ | hasRole('ADMIN') | 200 |
| DELETE | `/api/v1/admin/staff/{id}/skills/{serviceId}` | Xóa mềm kỹ năng dịch vụ | hasRole('ADMIN') | 200 |
| GET | `/api/v1/appointments/admin/all` | Tra cứu lịch hẹn theo phạm vi nhân viên | hasAnyRole('ADMIN', 'STAFF', 'SPA_RECEPTION', 'SPA_THERAPIST') | 200 |
| PUT | `/api/v1/appointments/admin/{id}/status` | Xác nhận, phân công hoặc chuyển trạng thái buổi Spa | hasAnyRole('ADMIN', 'STAFF', 'SPA_RECEPTION', 'SPA_THERAPIST') | 200 |
| POST | `/api/v1/appointments/book` | Đặt yêu cầu lịch hẹn Spa | isAuthenticated() | 200 |
| GET | `/api/v1/appointments/checkout-policy` | Xem quy tắc chốt hóa đơn Spa | isAuthenticated() | 200 |
| GET | `/api/v1/appointments/my-appointments` | Xem danh sách lịch hẹn của tôi | isAuthenticated() | 200 |
| GET | `/api/v1/appointments/{id}` | Xem chi tiết một lịch hẹn | isAuthenticated() | 200 |
| PUT | `/api/v1/appointments/{id}/cancel` | Hủy lịch hẹn trước check-in | isAuthenticated() | 200 |
| PUT | `/api/v1/appointments/{id}/check-in` | Ghi nhận khách đến Spa | hasAnyRole('ADMIN','STAFF','SPA_RECEPTION') | 200 |
| GET | `/api/v1/appointments/{id}/history` | Xem lịch sử hành động của lịch hẹn | isAuthenticated() | 200 |
| GET | `/api/v1/appointments/{id}/invoice` | Xem hóa đơn và số tiền Spa còn thiếu | isAuthenticated() | 200 |
| POST | `/api/v1/appointments/{id}/invoice` | Lập hóa đơn cố định cho buổi Spa | hasAnyRole('ADMIN','STAFF','SPA_RECEPTION') | 200 |
| POST | `/api/v1/appointments/{id}/invoice/cash-receipts` | Ghi nhận một phiếu thu tiền mặt Spa | hasAnyRole('ADMIN','STAFF','SPA_RECEPTION') | 200 |
| PUT | `/api/v1/appointments/{id}/items/{itemId}/execution` | Ghi kết quả thực hiện một dịch vụ | hasAnyRole('ADMIN','STAFF','SPA_THERAPIST') | 200 |
| PUT | `/api/v1/appointments/{id}/reschedule` | Đổi giờ lịch hẹn trước check-in | isAuthenticated() | 200 |
| GET | `/api/v1/spa/appointments/{id}/care-summary` | Xem hồ sơ chăm sóc và versionHash hiện tại | JWT; kiểm thêm phạm vi tại service | 200 |
| GET | `/api/v1/spa/appointments/{id}/follow-up-instructions` | Xem hướng dẫn sau buổi đã ghi | JWT; kiểm thêm phạm vi tại service | 200 |
| POST | `/api/v1/spa/appointments/{id}/follow-up-instructions/items/{itemId}` | Nhân viên ghi hướng dẫn cho item đã thực hiện | JWT; kiểm thêm phạm vi tại service | 201 |
| GET | `/api/v1/spa/appointments/{id}/preparation` | Xem yêu cầu chuẩn bị đã chụp theo từng item | JWT; kiểm thêm phạm vi tại service | 200 |
| POST | `/api/v1/spa/appointments/{id}/preparation/forms/{requirementId}/responses` | Khách nộp và xác nhận form đã đặt | JWT; kiểm thêm phạm vi tại service | 201 |
| POST | `/api/v1/spa/appointments/{id}/preparation/items/{itemId}/acknowledge-warnings` | Người thực hiện xác nhận đã đọc cảnh báo | JWT; kiểm thêm phạm vi tại service | 201 |
| POST | `/api/v1/spa/appointments/{id}/preparation/items/{itemId}/override` | ADMIN duyệt ngoại lệ yêu cầu chuẩn bị | JWT; kiểm thêm phạm vi tại service | 201 |
| GET | `/api/v1/spa/services` | Lấy danh sách dịch vụ Spa đang hoạt động | Công khai | 200 |
| GET | `/api/v1/spa/services/packages` | Lấy danh sách gói Spa có thể mua | Công khai | 200 |
| GET | `/api/v1/spa/services/slug/{slug}` | Xem chi tiết dịch vụ Spa theo slug | Công khai | 200 |
| GET | `/api/v1/spa/services/{id}` | Xem chi tiết dịch vụ Spa theo ID | Công khai | 200 |
| GET | `/api/v1/spa/services/{id}/available-slots` | Gợi ý giờ trống cho một dịch vụ | Công khai | 200 |
| GET | `/api/v1/spa/services/{id}/staff` | Lấy nhân viên có kỹ năng cho dịch vụ | Công khai | 200 |
| GET | `/api/v1/spa/tickets/my-active-tickets` | Xem vé của tôi còn lượt khả dụng | isAuthenticated() | 200 |
| GET | `/api/v1/spa/tickets/my-tickets` | Xem tất cả vé liệu trình của tôi | isAuthenticated() | 200 |
| POST | `/api/v1/spa/tickets/purchase` | Tạo đơn thanh toán mua gói Spa | isAuthenticated() | 200 |
| GET | `/api/v1/spa/tickets/{id}` | Xem chi tiết một vé liệu trình | isAuthenticated() | 200 |
| GET | `/api/v1/spa/tickets/{id}/movements` | Phân trang sổ lượt và điều chỉnh của vé | isAuthenticated() | 200 |

## Hồ sơ và chăm sóc khách hàng — 7 operations

| Method | Path | Hành động | Xác thực / guard | HTTP thành công |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/admin/crm/customers` | Tìm khách hàng, tối đa 100 kết quả | hasAnyRole('ADMIN','CS_STAFF') | 200 |
| GET | `/api/v1/admin/crm/customers/page` | Phân trang tìm kiếm hồ sơ khách hàng | hasAnyRole('ADMIN','CS_STAFF') | 200 |
| GET | `/api/v1/admin/crm/customers/{id}` | Xem thông tin và tổng hợp hoạt động khách | hasAnyRole('ADMIN','CS_STAFF') | 200 |
| GET | `/api/v1/admin/crm/customers/{id}/notes` | Xem tối đa 100 ghi chú chăm sóc mới nhất | hasAnyRole('ADMIN','CS_STAFF') | 200 |
| POST | `/api/v1/admin/crm/customers/{id}/notes` | Thêm ghi chú nội bộ về chăm sóc khách | hasAnyRole('ADMIN','CS_STAFF') | 200 |
| GET | `/api/v1/admin/crm/customers/{id}/notes/page` | Phân trang lịch sử ghi chú chăm sóc | hasAnyRole('ADMIN','CS_STAFF') | 200 |
| DELETE | `/api/v1/admin/crm/notes/{noteId}` | Xóa mềm ghi chú chăm sóc | hasAnyRole('ADMIN','CS_STAFF') | 200 |

## Kho và kiểm kê — 15 operations

| Method | Path | Hành động | Xác thực / guard | HTTP thành công |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/admin/inventory/expiring-soon` | Danh sách lô có hạn từ hôm nay đến ngưỡng | hasAnyRole('ADMIN', 'INVENTORY_STAFF') | 200 |
| GET | `/api/v1/admin/inventory/low-stock` | Cảnh báo tồn khả dụng ở mức tối thiểu trở xuống | hasAnyRole('ADMIN', 'INVENTORY_STAFF') | 200 |
| GET | `/api/v1/admin/inventory/transactions` | Lấy lịch sử biến động kho | hasAnyRole('ADMIN', 'INVENTORY_STAFF') | 200 |
| POST | `/api/v1/admin/inventory/{stockId}/inspection` | Duyệt bán lại hoặc loại bỏ hàng cách ly | hasAnyRole('ADMIN', 'INVENTORY_STAFF') | 200 |
| GET | `/api/v1/admin/warehouses` | Lấy danh sách kho chưa xóa | hasAnyRole('ADMIN', 'INVENTORY_STAFF') | 200 |
| POST | `/api/v1/admin/warehouses` | Tạo kho bãi mới (Admin) | hasAnyRole('ADMIN', 'INVENTORY_STAFF') | 201 |
| DELETE | `/api/v1/admin/warehouses/stocks/{stockId}` | Xóa mềm lô tồn kho | hasAnyRole('ADMIN', 'INVENTORY_STAFF') | 200 |
| PUT | `/api/v1/admin/warehouses/stocks/{stockId}/adjustment` | Điều chỉnh số tồn bán được sau kiểm đếm | hasAnyRole('ADMIN', 'INVENTORY_STAFF') | 200 |
| POST | `/api/v1/admin/warehouses/stocks/{stockId}/transfer` | Điều chuyển tồn khả dụng sang kho khác | hasAnyRole('ADMIN', 'INVENTORY_STAFF') | 200 |
| DELETE | `/api/v1/admin/warehouses/{id}` | Xóa mềm kho khi đã hết số dư | hasAnyRole('ADMIN', 'INVENTORY_STAFF') | 200 |
| GET | `/api/v1/admin/warehouses/{id}` | Xem thông tin kho bãi theo ID (Admin) | hasAnyRole('ADMIN', 'INVENTORY_STAFF') | 200 |
| PUT | `/api/v1/admin/warehouses/{id}` | Cập nhật thông tin kho | hasAnyRole('ADMIN', 'INVENTORY_STAFF') | 200 |
| POST | `/api/v1/admin/warehouses/{id}/receipts` | Nhập tăng số lượng một lô | hasAnyRole('ADMIN', 'INVENTORY_STAFF') | 200 |
| GET | `/api/v1/admin/warehouses/{id}/stocks` | Lấy danh sách tồn kho theo kho bãi (Admin) | hasAnyRole('ADMIN', 'INVENTORY_STAFF') | 200 |
| POST | `/api/v1/admin/warehouses/{id}/stocks` | Đặt số lượng tuyệt đối và thông tin lô | hasAnyRole('ADMIN', 'INVENTORY_STAFF') | 200 |

## Nhà cung cấp và mua hàng — 12 operations

| Method | Path | Hành động | Xác thực / guard | HTTP thành công |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/admin/procurement/orders` | Lấy danh sách phiếu mua hàng (lọc theo trạng thái, phân trang) | hasAnyRole('ADMIN','INVENTORY_STAFF') | 200 |
| POST | `/api/v1/admin/procurement/orders` | Tạo phiếu mua DRAFT | hasAnyRole('ADMIN','INVENTORY_STAFF') | 200 |
| GET | `/api/v1/admin/procurement/orders/{id}` | Xem chi tiết phiếu mua hàng theo ID | hasAnyRole('ADMIN','INVENTORY_STAFF') | 200 |
| PUT | `/api/v1/admin/procurement/orders/{id}` | Thay nội dung phiếu DRAFT | hasAnyRole('ADMIN','INVENTORY_STAFF') | 200 |
| PUT | `/api/v1/admin/procurement/orders/{id}/approve` | Phê duyệt DRAFT→APPROVED | hasAnyRole('ADMIN','INVENTORY_STAFF') | 200 |
| PUT | `/api/v1/admin/procurement/orders/{id}/cancel` | Hủy phiếu chưa nhận | hasAnyRole('ADMIN','INVENTORY_STAFF') | 200 |
| PUT | `/api/v1/admin/procurement/orders/{id}/receive` | Nhận toàn bộ hàng APPROVED→RECEIVED | hasAnyRole('ADMIN','INVENTORY_STAFF') | 200 |
| GET | `/api/v1/admin/procurement/suppliers` | Lấy danh sách nhà cung cấp (phân trang) | hasAnyRole('ADMIN','INVENTORY_STAFF') | 200 |
| POST | `/api/v1/admin/procurement/suppliers` | Tạo nhà cung cấp | hasAnyRole('ADMIN','INVENTORY_STAFF') | 200 |
| DELETE | `/api/v1/admin/procurement/suppliers/{id}` | Xóa nhà cung cấp | hasAnyRole('ADMIN','INVENTORY_STAFF') | 200 |
| GET | `/api/v1/admin/procurement/suppliers/{id}` | Xem chi tiết nhà cung cấp theo ID | hasAnyRole('ADMIN','INVENTORY_STAFF') | 200 |
| PUT | `/api/v1/admin/procurement/suppliers/{id}` | Thay thông tin nhà cung cấp | hasAnyRole('ADMIN','INVENTORY_STAFF') | 200 |

## Khuyến mãi — 5 operations

| Method | Path | Hành động | Xác thực / guard | HTTP thành công |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/admin/vouchers` | Lấy danh sách mã giảm giá Voucher (phân trang) | hasRole('ADMIN') | 200 |
| POST | `/api/v1/admin/vouchers` | Tạo voucher | hasRole('ADMIN') | 201 |
| DELETE | `/api/v1/admin/vouchers/{id}` | Xóa mềm voucher | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/vouchers/{id}` | Xem chi tiết Voucher theo ID | hasRole('ADMIN') | 200 |
| PUT | `/api/v1/admin/vouchers/{id}` | Thay cấu hình voucher | hasRole('ADMIN') | 200 |

## Đánh giá và kiểm duyệt — 7 operations

| Method | Path | Hành động | Xác thực / guard | HTTP thành công |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/admin/reviews` | Lấy danh sách đánh giá quản trị | hasAnyRole('ADMIN', 'CS_STAFF') | 200 |
| DELETE | `/api/v1/admin/reviews/{id}` | Xóa đánh giá sản phẩm (Soft delete) | hasAnyRole('ADMIN', 'CS_STAFF') | 200 |
| GET | `/api/v1/admin/reviews/{id}` | Xem chi tiết đánh giá theo ID | hasAnyRole('ADMIN', 'CS_STAFF') | 200 |
| PUT | `/api/v1/admin/reviews/{id}/reply` | Gửi phản hồi của Quản trị viên / CSKH cho đánh giá | hasAnyRole('ADMIN', 'CS_STAFF') | 200 |
| PUT | `/api/v1/admin/reviews/{id}/status` | Kiểm duyệt trạng thái đánh giá (APPROVED hoặc REJECTED) | hasAnyRole('ADMIN', 'CS_STAFF') | 200 |
| GET | `/api/v1/reviews` | Xem đánh giá công khai của sản phẩm | Công khai | 200 |
| POST | `/api/v1/reviews` | Gửi đánh giá từ đơn hàng đã giao | isAuthenticated() | 201 |

## Dashboard — 5 operations

| Method | Path | Hành động | Xác thực / guard | HTTP thành công |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/admin/dashboard` | Lấy tổng quan điều hành | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/dashboard/revenue` | Lấy xu hướng tiền vào và tiền hoàn | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/dashboard/spa-financials` | Lấy số dư tài chính Spa hiện tại | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/dashboard/spa-occupancy` | Lấy độ lấp lịch và thời gian phục vụ Spa | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/dashboard/top-products` | Lấy 10 sản phẩm bán chạy | hasRole('ADMIN') | 200 |

## Cấu hình, cảnh báo và kiểm toán — 8 operations

| Method | Path | Hành động | Xác thực / guard | HTTP thành công |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/admin/audit-logs` | Tra cứu nhật ký kiểm toán | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/audit-logs/{id}` | Xem chi tiết một bản ghi kiểm toán theo ID | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/operational-alerts` | Lấy cảnh báo vận hành đang có số lượng | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/system-configs` | Lấy toàn bộ danh sách cấu hình hệ thống | hasRole('ADMIN') | 200 |
| POST | `/api/v1/admin/system-configs` | Tạo cấu hình nghiệp vụ | hasRole('ADMIN') | 201 |
| DELETE | `/api/v1/admin/system-configs/{key}` | Ngừng sử dụng cấu hình | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/system-configs/{key}` | Xem chi tiết một cấu hình hệ thống theo Key | hasRole('ADMIN') | 200 |
| PUT | `/api/v1/admin/system-configs/{key}` | Cập nhật giá trị cấu hình | hasRole('ADMIN') | 200 |

## Chatbot — 6 operations

| Method | Path | Hành động | Xác thực / guard | HTTP thành công |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/chatbot/chat` | Trò chuyện tư vấn sản phẩm (Non-streaming) | Công khai | 200 |
| POST | `/api/v1/chatbot/chat/stream` | Trò chuyện tư vấn sản phẩm (Streaming SSE) | Công khai | 200 |
| GET | `/api/v1/chatbot/health` | Kiểm tra tình trạng hoạt động của dịch vụ Chatbot AI | Công khai | 200 |
| GET | `/api/v1/chatbot/openapi.json` | Tải đặc tả kỹ thuật OpenAPI JSON gốc của Chatbot | Công khai | 200 |
| POST | `/api/v1/chatbot/sync/database` | Kích hoạt đồng bộ dữ liệu từ MySQL vào kho vector RAG | hasRole('ADMIN') | 200 |
| POST | `/api/v1/chatbot/test/understand` | Kiểm tra phân tích ý định người dùng (NLU / Understanding) | hasRole('ADMIN') | 200 |

