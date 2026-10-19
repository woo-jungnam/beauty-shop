# Danh mục endpoint API

Sinh từ OpenAPI đã kiểm chứng lúc 2026-10-17T13:53:46+07:00. **407 operations trên 313 đường dẫn.**

Nguồn chi tiết: [OpenAPI JSON](openapi.json), [hướng dẫn Swagger](API_GUIDE.md). Bảng ghi HTTP thành công; lỗi và schema request/response tra trong Swagger. Role ở guard chưa thay điều kiện ownership/phân công/state của service.

## Xác thực, tài khoản và vai trò — 42 operations

| Method | Path | Hành động | Xác thực / guard | HTTP thành công |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/admin/users` | Lấy danh sách người dùng (ADMIN) | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/users/staff-permissions` | Tra cứu /api/v1/admin/users/staff-permissions | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/users/{id}` | Xem thông tin chi tiết người dùng theo ID | hasRole('ADMIN') | 200 |
| POST | `/api/v1/admin/users/{id}/force-logout` | Buộc đăng xuất toàn bộ phiên của người dùng | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/users/{id}/permissions` | Tra cứu /api/v1/admin/users/{id}/permissions | hasRole('ADMIN') | 200 |
| PUT | `/api/v1/admin/users/{id}/permissions` | Cập nhật /api/v1/admin/users/{id}/permissions | hasRole('ADMIN') | 200 |
| POST | `/api/v1/admin/users/{id}/reset-password` | Đặt lại mật khẩu tạm thời cho người dùng | hasRole('ADMIN') | 200 |
| PUT | `/api/v1/admin/users/{id}/roles` | Thay thế toàn bộ vai trò của người dùng | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/users/{id}/sessions` | Xem lịch sử phiên đăng nhập của người dùng | hasRole('ADMIN') | 200 |
| DELETE | `/api/v1/admin/users/{id}/sessions/{sessionId}` | Thu hồi một họ phiên của đúng người dùng | hasRole('ADMIN') | 200 |
| PUT | `/api/v1/admin/users/{id}/status` | Cập nhật trạng thái tài khoản (ACTIVE, BLOCKED, SUSPENDED) | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/users/{id}/status-history` | Xem lịch sử thay đổi trạng thái tài khoản | hasRole('ADMIN') | 200 |
| POST | `/api/v1/auth/forgot-password` | Thực hiện /api/v1/auth/forgot-password | Công khai | 200 |
| POST | `/api/v1/auth/login` | Đăng nhập tài khoản và nhận cặp JWT | Công khai | 200 |
| POST | `/api/v1/auth/logout` | Đăng xuất một họ phiên | Công khai | 200 |
| GET | `/api/v1/auth/profile` | Lấy thông tin hồ sơ người dùng đang đăng nhập | isAuthenticated() | 200 |
| POST | `/api/v1/auth/refresh` | Luân chuyển refresh token và cấp cặp token mới | Công khai | 200 |
| POST | `/api/v1/auth/register` | Đăng ký tài khoản khách hàng mới | Công khai | 201 |
| POST | `/api/v1/auth/reset-password` | Thực hiện /api/v1/auth/reset-password | Công khai | 200 |
| POST | `/api/v1/newsletter/subscriptions` | Thực hiện /api/v1/newsletter/subscriptions | Công khai | 200 |
| GET | `/api/v1/roles` | Lấy danh sách tất cả vai trò (Admin) | hasRole('ADMIN') | 200 |
| POST | `/api/v1/roles` | Tạo vai trò mới (ADMIN) | hasRole('ADMIN') | 201 |
| DELETE | `/api/v1/roles/{id}` | Xóa vai trò tùy chỉnh (ADMIN) | hasRole('ADMIN') | 200 |
| GET | `/api/v1/roles/{id}` | Lấy thông tin vai trò theo ID (Admin) | hasRole('ADMIN') | 200 |
| PUT | `/api/v1/roles/{id}` | Cập nhật vai trò (ADMIN) | hasRole('ADMIN') | 200 |
| GET | `/api/v1/test/admin` | Kiểm tra truy cập quyền quản trị (Chỉ Admin) | hasRole('ADMIN') | 200 |
| GET | `/api/v1/test/public` | Kiểm tra truy cập công khai (Public) | Công khai | 200 |
| GET | `/api/v1/test/user` | Kiểm tra truy cập ROLE_USER hoặc ROLE_ADMIN | hasAnyRole('USER', 'ADMIN') | 200 |
| GET | `/api/v1/users/addresses` | Tra cứu /api/v1/users/addresses | isAuthenticated() | 200 |
| POST | `/api/v1/users/addresses` | Thực hiện /api/v1/users/addresses | isAuthenticated() | 200 |
| DELETE | `/api/v1/users/addresses/{id}` | Xóa /api/v1/users/addresses/{id} | isAuthenticated() | 200 |
| PUT | `/api/v1/users/addresses/{id}` | Cập nhật /api/v1/users/addresses/{id} | isAuthenticated() | 200 |
| GET | `/api/v1/users/admin/all` | Lấy danh sách người dùng qua tuyến tương thích (ADMIN) | hasRole('ADMIN') | 200 |
| GET | `/api/v1/users/admin/{id}` | Xem thông tin người dùng theo ID (Admin) | hasRole('ADMIN') | 200 |
| POST | `/api/v1/users/admin/{id}/force-logout` | Buộc đăng xuất người dùng trên tất cả thiết bị (ADMIN) | hasRole('ADMIN') | 200 |
| GET | `/api/v1/users/me` | Lấy thông tin cá nhân hiện tại | isAuthenticated() | 200 |
| GET | `/api/v1/users/order-statistics` | Tra cứu /api/v1/users/order-statistics | isAuthenticated() | 200 |
| PUT | `/api/v1/users/password` | Cập nhật /api/v1/users/password | isAuthenticated() | 200 |
| GET | `/api/v1/users/profile` | Lấy thông tin cá nhân hiện tại | isAuthenticated() | 200 |
| PUT | `/api/v1/users/profile` | Cập nhật thông tin cá nhân | isAuthenticated() | 200 |
| GET | `/api/v1/users/skin-profile` | Tra cứu /api/v1/users/skin-profile | isAuthenticated() | 200 |
| PUT | `/api/v1/users/skin-profile` | Cập nhật /api/v1/users/skin-profile | isAuthenticated() | 200 |

## Thông báo cá nhân — 2 operations

| Method | Path | Hành động | Xác thực / guard | HTTP thành công |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/users/notifications` | Tra cứu /api/v1/users/notifications | isAuthenticated() | 200 |
| PUT | `/api/v1/users/notifications/{id}/read` | Cập nhật /api/v1/users/notifications/{id}/read | isAuthenticated() | 200 |

## Sản phẩm và danh mục — 87 operations

| Method | Path | Hành động | Xác thực / guard | HTTP thành công |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/admin/banners` | Lấy toàn bộ danh sách banner cho quản trị | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| POST | `/api/v1/admin/banners` | Tạo banner mới | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| DELETE | `/api/v1/admin/banners/{id}` | Xóa banner (soft-delete) | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| GET | `/api/v1/admin/banners/{id}` | Xem chi tiết banner | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| PUT | `/api/v1/admin/banners/{id}` | Cập nhật banner | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| GET | `/api/v1/admin/dermatology/products/{productId}` | Lấy toàn bộ hồ sơ da liễu và quy trình sử dụng của sản phẩm | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| PUT | `/api/v1/admin/dermatology/products/{productId}/skin-compatibility` | Cập nhật danh sách tương thích loại da (Skin Compatibility) | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| PUT | `/api/v1/admin/dermatology/products/{productId}/skin-concerns` | Cập nhật danh sách giải quyết vấn đề da (Skin Concerns) | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| PUT | `/api/v1/admin/dermatology/products/{productId}/usage` | Cập nhật quy trình HDSD và cảnh báo (Usage Detail) | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| GET | `/api/v1/admin/dermatology/skin-concerns` | Lấy danh mục chuẩn các vấn đề da liễu | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| GET | `/api/v1/admin/dermatology/skin-types` | Lấy danh mục chuẩn các loại da | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| GET | `/api/v1/admin/ingredients` | Lấy danh sách hoạt chất (phân trang) | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| POST | `/api/v1/admin/ingredients` | Tạo hoạt chất mỹ phẩm mới | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 201 |
| GET | `/api/v1/admin/ingredients/products/{productId}` | Lấy danh sách hoạt chất trong sản phẩm | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| PUT | `/api/v1/admin/ingredients/products/{productId}` | Thay toàn bộ danh sách hoạt chất cho sản phẩm | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| DELETE | `/api/v1/admin/ingredients/{id}` | Xóa hoạt chất mỹ phẩm | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| GET | `/api/v1/admin/ingredients/{id}` | Xem chi tiết hoạt chất theo ID | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| PUT | `/api/v1/admin/ingredients/{id}` | Cập nhật thông tin hoạt chất | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| POST | `/api/v1/admin/media/upload` | Tải tệp hình ảnh lên máy chủ | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 201 |
| GET | `/api/v1/admin/products` | Lấy danh sách sản phẩm quản trị (phân trang) | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| POST | `/api/v1/admin/products` | Tạo sản phẩm mới | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 201 |
| DELETE | `/api/v1/admin/products/{id}` | Xóa sản phẩm (Soft delete) | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| GET | `/api/v1/admin/products/{id}` | Xem chi tiết sản phẩm theo ID | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| PUT | `/api/v1/admin/products/{id}` | Cập nhật thông tin sản phẩm | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| PUT | `/api/v1/admin/products/{id}/attributes` | Thay toàn bộ thuộc tính sản phẩm và SKU | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| POST | `/api/v1/admin/products/{id}/images` | Thêm ảnh vào thư viện sản phẩm | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 201 |
| DELETE | `/api/v1/admin/products/{id}/images/{imageId}` | Xóa ảnh khỏi thư viện sản phẩm | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| POST | `/api/v1/admin/products/{id}/variants` | Thêm biến thể SKU mới cho sản phẩm | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 201 |
| DELETE | `/api/v1/admin/products/{id}/variants/{variantId}` | Xóa biến thể SKU | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| PUT | `/api/v1/admin/products/{id}/variants/{variantId}` | Cập nhật biến thể SKU | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| GET | `/api/v1/admin/recommendations/config` | Xem cấu hình gợi ý sản phẩm hiện tại | hasRole('ADMIN'); STAFF cần CATALOG | 200 |
| PUT | `/api/v1/admin/recommendations/config` | Lưu và áp dụng cấu hình gợi ý sản phẩm | hasRole('ADMIN'); STAFF cần CATALOG | 200 |
| GET | `/api/v1/admin/recommendations/preview` | Xem thử kết quả và điểm gợi ý sản phẩm | hasRole('ADMIN'); STAFF cần CATALOG | 200 |
| POST | `/api/v1/admin/recommendations/refresh` | Yêu cầu làm mới dữ liệu gợi ý sản phẩm | hasRole('ADMIN'); STAFF cần CATALOG | 200 |
| GET | `/api/v1/admin/recommendations/status` | Xem trạng thái dịch vụ gợi ý sản phẩm | hasRole('ADMIN'); STAFF cần CATALOG | 200 |
| GET | `/api/v1/attributes` | Lấy tất cả định nghĩa thuộc tính | Công khai | 200 |
| POST | `/api/v1/attributes` | Tạo định nghĩa thuộc tính mới (ADMIN / STAFF) | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 201 |
| DELETE | `/api/v1/attributes/values/{valueId}` | Xóa giá trị thuộc tính (ADMIN / STAFF) | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| DELETE | `/api/v1/attributes/{id}` | Xóa định nghĩa thuộc tính (ADMIN / STAFF) | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| GET | `/api/v1/attributes/{id}` | Lấy định nghĩa thuộc tính theo ID | Công khai | 200 |
| PUT | `/api/v1/attributes/{id}` | Cập nhật định nghĩa thuộc tính (ADMIN / STAFF) | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| GET | `/api/v1/attributes/{id}/values` | Lấy danh sách giá trị theo định nghĩa thuộc tính | Công khai | 200 |
| POST | `/api/v1/attributes/{id}/values` | Thêm giá trị thuộc tính (ADMIN / STAFF) | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 201 |
| GET | `/api/v1/banners` | Lấy danh sách banner đang hoạt động | Công khai | 200 |
| GET | `/api/v1/banners/{id}` | Xem chi tiết banner theo ID | Công khai | 200 |
| GET | `/api/v1/brands` | Lấy danh sách tất cả thương hiệu (phân trang) | Công khai | 200 |
| POST | `/api/v1/brands` | Tạo thương hiệu mới (ADMIN / STAFF) | hasAnyRole('ADMIN','STAFF'); STAFF cần CATALOG | 201 |
| GET | `/api/v1/brands/slug/{slug}` | Xem chi tiết thương hiệu theo Slug | Công khai | 200 |
| DELETE | `/api/v1/brands/{id}` | Xóa thương hiệu (ADMIN / STAFF) | hasAnyRole('ADMIN','STAFF'); STAFF cần CATALOG | 200 |
| GET | `/api/v1/brands/{id}` | Xem chi tiết thương hiệu theo ID | Công khai | 200 |
| PUT | `/api/v1/brands/{id}` | Cập nhật thông tin thương hiệu (ADMIN / STAFF) | hasAnyRole('ADMIN','STAFF'); STAFF cần CATALOG | 200 |
| GET | `/api/v1/categories` | Lấy danh sách tất cả danh mục | Công khai | 200 |
| POST | `/api/v1/categories` | Tạo danh mục mới (ADMIN / STAFF) | hasAnyRole('ADMIN','STAFF'); STAFF cần CATALOG | 201 |
| GET | `/api/v1/categories/root` | Lấy danh sách các danh mục gốc | Công khai | 200 |
| GET | `/api/v1/categories/slug/{slug}` | Xem chi tiết danh mục theo Slug | Công khai | 200 |
| DELETE | `/api/v1/categories/{id}` | Xóa danh mục (ADMIN / STAFF) | hasAnyRole('ADMIN','STAFF'); STAFF cần CATALOG | 200 |
| GET | `/api/v1/categories/{id}` | Xem chi tiết danh mục theo ID | Công khai | 200 |
| PUT | `/api/v1/categories/{id}` | Cập nhật danh mục (ADMIN / STAFF) | hasAnyRole('ADMIN','STAFF'); STAFF cần CATALOG | 200 |
| GET | `/api/v1/products` | Lấy danh sách tất cả sản phẩm (phân trang) | Công khai | 200 |
| POST | `/api/v1/products` | Tạo sản phẩm mới (Admin/Staff) | hasAnyRole('ADMIN', 'STAFF') | 201 |
| GET | `/api/v1/products/brand/{brandId}` | Lấy danh sách sản phẩm theo thương hiệu | Công khai | 200 |
| GET | `/api/v1/products/category/{categoryId}` | Lấy danh sách sản phẩm theo danh mục | Công khai | 200 |
| GET | `/api/v1/products/expiring-soon` | Lấy danh sách sản phẩm xả kho cận date (Clearance / Flash Sale) | Công khai | 200 |
| GET | `/api/v1/products/featured` | Lấy sản phẩm nổi bật | Công khai | 200 |
| GET | `/api/v1/products/recommended-for-you` | Gợi ý sản phẩm dành cho bạn (Personalized Recommendation) | Công khai | 200 |
| GET | `/api/v1/products/search` | Tìm kiếm và lọc sản phẩm | Công khai | 200 |
| GET | `/api/v1/products/slug/{slug}` | Xem chi tiết sản phẩm theo Slug | Công khai | 200 |
| POST | `/api/v1/products/tracking/interaction` | Ghi nhận hành vi xem và chọn kết quả tìm kiếm | JWT; kiểm thêm phạm vi tại service | 200 |
| DELETE | `/api/v1/products/{id}` | Xóa sản phẩm (Admin/Staff) | hasAnyRole('ADMIN', 'STAFF') | 200 |
| GET | `/api/v1/products/{id}` | Xem chi tiết sản phẩm theo ID | Công khai | 200 |
| PUT | `/api/v1/products/{id}` | Cập nhật thông tin sản phẩm (Admin/Staff) | hasAnyRole('ADMIN', 'STAFF') | 200 |
| GET | `/api/v1/products/{id}/images` | Lấy danh sách hình ảnh của sản phẩm | Công khai | 200 |
| POST | `/api/v1/products/{id}/images` | Thêm hình ảnh cho sản phẩm (Admin/Staff) | hasAnyRole('ADMIN', 'STAFF') | 201 |
| DELETE | `/api/v1/products/{id}/images/{imageId}` | Xóa hình ảnh của sản phẩm (Admin/Staff) | hasAnyRole('ADMIN', 'STAFF') | 200 |
| GET | `/api/v1/products/{id}/similar` | Gợi ý sản phẩm tương tự (Content-Based Item-to-Item) | Công khai | 200 |
| GET | `/api/v1/products/{id}/variants` | Lấy danh sách biến thể của sản phẩm | Công khai | 200 |
| POST | `/api/v1/products/{id}/variants` | Thêm biến thể mới cho sản phẩm (Admin/Staff) | hasAnyRole('ADMIN', 'STAFF') | 201 |
| DELETE | `/api/v1/products/{id}/variants/{variantId}` | Xóa biến thể sản phẩm (Admin/Staff) | hasAnyRole('ADMIN', 'STAFF') | 200 |
| PUT | `/api/v1/products/{id}/variants/{variantId}` | Cập nhật biến thể sản phẩm (Admin/Staff) | hasAnyRole('ADMIN', 'STAFF') | 200 |
| GET | `/api/v1/tags` | Lấy danh sách tất cả thẻ | Công khai | 200 |
| POST | `/api/v1/tags` | Tạo thẻ sản phẩm mới (Admin/Staff) | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 201 |
| DELETE | `/api/v1/tags/{id}` | Xóa thẻ sản phẩm (Admin/Staff) | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| GET | `/api/v1/tags/{id}` | Lấy thông tin thẻ theo ID | Công khai | 200 |
| PUT | `/api/v1/tags/{id}` | Cập nhật thẻ sản phẩm (Admin/Staff) | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| GET | `/api/v1/users/wishlist` | Tra cứu /api/v1/users/wishlist | isAuthenticated() | 200 |
| DELETE | `/api/v1/users/wishlist/{productId}` | Xóa /api/v1/users/wishlist/{productId} | isAuthenticated() | 200 |
| PUT | `/api/v1/users/wishlist/{productId}` | Cập nhật /api/v1/users/wishlist/{productId} | isAuthenticated() | 200 |

## Giỏ hàng — 6 operations

| Method | Path | Hành động | Xác thực / guard | HTTP thành công |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/cart` | Lấy giỏ hàng hiện tại | JWT tùy chọn / phiên khách | 200 |
| POST | `/api/v1/cart/add` | Thêm sản phẩm vào giỏ hàng | JWT tùy chọn / phiên khách | 200 |
| DELETE | `/api/v1/cart/clear` | Xóa toàn bộ giỏ hàng | JWT tùy chọn / phiên khách | 200 |
| DELETE | `/api/v1/cart/items/{itemId}` | Xóa sản phẩm khỏi giỏ hàng | JWT tùy chọn / phiên khách | 200 |
| PUT | `/api/v1/cart/items/{itemId}` | Cập nhật số lượng sản phẩm trong giỏ hàng | JWT tùy chọn / phiên khách | 200 |
| POST | `/api/v1/cart/merge` | Gộp giỏ khách vãng lai vào tài khoản | isAuthenticated() | 200 |

## Đơn hàng và hoàn tiền — 22 operations

| Method | Path | Hành động | Xác thực / guard | HTTP thành công |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/admin/commerce/orders/{id}/balances` | Tra cứu /api/v1/admin/commerce/orders/{id}/balances | hasAnyRole('ADMIN','STAFF'); STAFF cần ORDERS | 200 |
| POST | `/api/v1/admin/commerce/orders/{id}/cod-settlement` | Thực hiện /api/v1/admin/commerce/orders/{id}/cod-settlement | hasRole('ADMIN'); STAFF cần ORDERS | 200 |
| POST | `/api/v1/admin/commerce/orders/{id}/overpayment-refund` | Thực hiện /api/v1/admin/commerce/orders/{id}/overpayment-refund | hasRole('ADMIN'); STAFF cần ORDERS | 200 |
| POST | `/api/v1/admin/commerce/orders/{id}/reallocate` | Thực hiện /api/v1/admin/commerce/orders/{id}/reallocate | hasAnyRole('ADMIN','STAFF'); STAFF cần ORDERS | 200 |
| GET | `/api/v1/admin/commerce/returns` | Tra cứu /api/v1/admin/commerce/returns | hasAnyRole('ADMIN','STAFF'); STAFF cần ORDERS | 200 |
| POST | `/api/v1/admin/commerce/returns/{id}/decision` | Thực hiện /api/v1/admin/commerce/returns/{id}/decision | hasAnyRole('ADMIN','STAFF'); STAFF cần ORDERS | 200 |
| POST | `/api/v1/admin/commerce/returns/{id}/receive` | Thực hiện /api/v1/admin/commerce/returns/{id}/receive | hasAnyRole('ADMIN','STAFF'); STAFF cần ORDERS | 200 |
| POST | `/api/v1/admin/commerce/returns/{id}/refund` | Thực hiện /api/v1/admin/commerce/returns/{id}/refund | hasRole('ADMIN'); STAFF cần ORDERS | 200 |
| POST | `/api/v1/admin/commerce/returns/{id}/replacement` | Thực hiện /api/v1/admin/commerce/returns/{id}/replacement | hasAnyRole('ADMIN','STAFF'); STAFF cần ORDERS | 200 |
| GET | `/api/v1/admin/orders` | Tìm đơn hàng quản trị | hasAnyRole('ADMIN', 'STAFF'); STAFF cần ORDERS | 200 |
| GET | `/api/v1/admin/orders/{id}` | Xem chi tiết đơn cho ADMIN/ORDER_STAFF | hasAnyRole('ADMIN', 'STAFF'); STAFF cần ORDERS | 200 |
| POST | `/api/v1/admin/orders/{id}/refund-confirmation` | Ghi nhận xác nhận đã hoàn tiền thủ công | hasRole('ADMIN'); STAFF cần ORDERS | 200 |
| PUT | `/api/v1/admin/orders/{id}/status` | Điều phối trạng thái đơn cho ADMIN/ORDER_STAFF | hasAnyRole('ADMIN', 'STAFF'); STAFF cần ORDERS | 200 |
| GET | `/api/v1/orders` | Lấy tất cả đơn hàng (Admin/Staff) | hasAnyRole('ADMIN', 'STAFF'); STAFF cần ORDERS | 200 |
| POST | `/api/v1/orders/checkout` | Thanh toán & tạo đơn hàng mới | JWT tùy chọn / phiên khách | 201 |
| GET | `/api/v1/orders/my-orders` | Xem danh sách đơn hàng của tôi | isAuthenticated() | 200 |
| GET | `/api/v1/orders/user/{userId}` | Xem đơn hàng theo User ID (Admin hoặc chính chủ) | isAuthenticated() | 200 |
| GET | `/api/v1/orders/{id}` | Xem chi tiết đơn hàng | JWT tùy chọn / phiên khách | 200 |
| DELETE | `/api/v1/orders/{id}/cancel` | Hủy đơn hàng | isAuthenticated() | 200 |
| PUT | `/api/v1/orders/{id}/status` | Cập nhật trạng thái đơn hàng (Admin/Staff) | hasAnyRole('ADMIN', 'STAFF'); STAFF cần ORDERS | 200 |
| GET | `/api/v1/orders/{orderId}/returns` | Tra cứu /api/v1/orders/{orderId}/returns | JWT tùy chọn / phiên khách | 200 |
| POST | `/api/v1/orders/{orderId}/returns` | Thực hiện /api/v1/orders/{orderId}/returns | JWT tùy chọn / phiên khách | 200 |

## Thanh toán và webhook — 7 operations

| Method | Path | Hành động | Xác thực / guard | HTTP thành công |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/admin/payments` | Lấy sổ giao dịch thanh toán | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/payments/exceptions` | Tra cứu /api/v1/admin/payments/exceptions | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/payments/resolutions` | Tra cứu /api/v1/admin/payments/resolutions | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/payments/summary` | Tổng hợp thực thu và hoàn tiền | hasRole('ADMIN') | 200 |
| GET | `/api/v1/admin/payments/{id}` | Xem chi tiết giao dịch thanh toán theo ID | hasRole('ADMIN') | 200 |
| POST | `/api/v1/admin/payments/{id}/resolution` | Thực hiện /api/v1/admin/payments/{id}/resolution | hasRole('ADMIN') | 200 |
| POST | `/api/v1/payment/sepay-webhook` | Tiếp nhận webhook SePay | API key SePay | 200 |

## Dịch vụ, liệu trình và lịch hẹn Spa — 154 operations

| Method | Path | Hành động | Xác thực / guard | HTTP thành công |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/admin/spa/appointments/{id}/items/{itemId}/service-refunds` | Ghi nhận hoàn tiền một phần theo dịch vụ | hasRole('ADMIN'); STAFF cần SPA_RECEPTION | 200 |
| GET | `/api/v1/admin/spa/appointments/{id}/service-refunds` | Xem hạn mức hoàn từng dòng hóa đơn Spa | hasRole('ADMIN'); STAFF cần SPA_RECEPTION | 200 |
| GET | `/api/v1/admin/spa/calendar/exceptions` | Tra cứu /api/v1/admin/spa/calendar/exceptions | hasAnyRole('ADMIN','STAFF'); STAFF cần SPA_RECEPTION | 200 |
| DELETE | `/api/v1/admin/spa/calendar/exceptions/{date}` | Xóa /api/v1/admin/spa/calendar/exceptions/{date} | hasRole('ADMIN'); STAFF cần SPA_RECEPTION | 200 |
| PUT | `/api/v1/admin/spa/calendar/exceptions/{date}` | Cập nhật /api/v1/admin/spa/calendar/exceptions/{date} | hasRole('ADMIN'); STAFF cần SPA_RECEPTION | 200 |
| GET | `/api/v1/admin/spa/calendar/weekly` | Tra cứu /api/v1/admin/spa/calendar/weekly | hasAnyRole('ADMIN','STAFF'); STAFF cần SPA_RECEPTION | 200 |
| PUT | `/api/v1/admin/spa/calendar/weekly` | Cập nhật /api/v1/admin/spa/calendar/weekly | hasRole('ADMIN'); STAFF cần SPA_RECEPTION | 200 |
| GET | `/api/v1/admin/spa/categories` | Lấy danh sách danh mục Spa (phân trang) | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| POST | `/api/v1/admin/spa/categories` | Tạo danh mục Spa mới | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 201 |
| DELETE | `/api/v1/admin/spa/categories/{id}` | Xóa mềm danh mục Spa | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| GET | `/api/v1/admin/spa/categories/{id}` | Xem chi tiết danh mục Spa theo ID | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| PUT | `/api/v1/admin/spa/categories/{id}` | Cập nhật danh mục Spa | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| GET | `/api/v1/admin/spa/facilities` | Danh sách tài nguyên Spa chưa xóa | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| POST | `/api/v1/admin/spa/facilities` | Tạo giường, phòng hoặc thiết bị | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 201 |
| DELETE | `/api/v1/admin/spa/facilities/{id}` | Xóa mềm tài nguyên Spa | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| GET | `/api/v1/admin/spa/facilities/{id}` | Xem chi tiết tài nguyên Spa | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| PUT | `/api/v1/admin/spa/facilities/{id}` | Cập nhật tài nguyên Spa | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| GET | `/api/v1/admin/spa/facilities/{id}/blocks` | Xem các khoảng bảo trì của tài nguyên | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| POST | `/api/v1/admin/spa/facilities/{id}/blocks` | Tạo khoảng ngừng phục vụ tài nguyên | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 201 |
| DELETE | `/api/v1/admin/spa/facilities/{id}/blocks/{blockId}` | Xóa mềm khoảng bảo trì | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| PUT | `/api/v1/admin/spa/facilities/{id}/blocks/{blockId}` | Đổi khoảng bảo trì tài nguyên | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| PUT | `/api/v1/admin/spa/financial-policy` | Cập nhật /api/v1/admin/spa/financial-policy | hasRole('ADMIN'); STAFF cần SPA_RECEPTION | 200 |
| GET | `/api/v1/admin/spa/financial-report` | Tra cứu /api/v1/admin/spa/financial-report | hasRole('ADMIN'); STAFF cần SPA_RECEPTION | 200 |
| GET | `/api/v1/admin/spa/packages` | Lấy danh sách gói liệu trình Spa | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| POST | `/api/v1/admin/spa/packages` | Tạo gói liệu trình Spa mới | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 201 |
| DELETE | `/api/v1/admin/spa/packages/{id}` | Xóa mềm gói liệu trình Spa | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| GET | `/api/v1/admin/spa/packages/{id}` | Xem chi tiết gói liệu trình Spa theo ID | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| PUT | `/api/v1/admin/spa/packages/{id}` | Cập nhật gói liệu trình Spa | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| GET | `/api/v1/admin/spa/preparation/services/{id}` | Xem yêu cầu form/cảnh báo của dịch vụ | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| PUT | `/api/v1/admin/spa/preparation/services/{id}` | Thay chính sách chuẩn bị cho booking mới | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| GET | `/api/v1/admin/spa/preparation/templates` | Phân trang các template form hiện hành | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| POST | `/api/v1/admin/spa/preparation/templates` | Tạo template và phiên bản form đầu tiên | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 201 |
| GET | `/api/v1/admin/spa/preparation/templates/{id}/versions` | Xem lịch sử phiên bản của template | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| POST | `/api/v1/admin/spa/preparation/templates/{id}/versions` | Tạo phiên bản form mới | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 201 |
| GET | `/api/v1/admin/spa/services` | Lấy danh sách dịch vụ Spa (phân trang) | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| POST | `/api/v1/admin/spa/services` | Tạo dịch vụ Spa mới | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 201 |
| DELETE | `/api/v1/admin/spa/services/{id}` | Xóa mềm dịch vụ Spa | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| GET | `/api/v1/admin/spa/services/{id}` | Xem chi tiết dịch vụ Spa theo ID | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| PUT | `/api/v1/admin/spa/services/{id}` | Cập nhật dịch vụ Spa | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| GET | `/api/v1/admin/spa/services/{id}/materials` | Tra cứu /api/v1/admin/spa/services/{id}/materials | hasAnyRole('ADMIN','STAFF'); STAFF cần SPA_RECEPTION | 200 |
| PUT | `/api/v1/admin/spa/services/{id}/materials` | Cập nhật /api/v1/admin/spa/services/{id}/materials | hasAnyRole('ADMIN','STAFF'); STAFF cần SPA_RECEPTION | 200 |
| GET | `/api/v1/admin/spa/services/{id}/resource-requirements` | Xem yêu cầu tài nguyên hiện tại của dịch vụ | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| PUT | `/api/v1/admin/spa/services/{id}/resource-requirements` | Thay toàn bộ yêu cầu tài nguyên dịch vụ | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| GET | `/api/v1/admin/spa/tickets` | Tìm kiếm vé Spa theo khách/trạng thái | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| GET | `/api/v1/admin/spa/tickets/{id}` | Xem chi tiết vé Spa của khách | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| PUT | `/api/v1/admin/spa/tickets/{id}/compensate` | Bù lượt cho một dịch vụ trong vé | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| PUT | `/api/v1/admin/spa/tickets/{id}/extend` | Gia hạn vé Spa với bằng chứng điều chỉnh | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| GET | `/api/v1/admin/spa/tickets/{id}/settlement` | Báo giá quyết toán gói lượt | hasRole('ADMIN'); STAFF cần SPA_RECEPTION | 200 |
| POST | `/api/v1/admin/spa/tickets/{id}/settlement` | Xác nhận quyết toán và thu hồi vé | hasRole('ADMIN'); STAFF cần SPA_RECEPTION | 200 |
| GET | `/api/v1/admin/spa/treatment-templates` | Quản trị xem mẫu lộ trình và các bản nháp | hasRole('ADMIN'); STAFF cần SPA_RECEPTION | 200 |
| POST | `/api/v1/admin/spa/treatment-templates` | Tạo mẫu lộ trình gồm từng buổi và dịch vụ thực hiện | hasRole('ADMIN'); STAFF cần SPA_RECEPTION | 200 |
| GET | `/api/v1/admin/spa/treatment-templates/{id}` | Xem bản nháp hoặc phiên bản hiện tại của mẫu | hasRole('ADMIN'); STAFF cần SPA_RECEPTION | 200 |
| PUT | `/api/v1/admin/spa/treatment-templates/{id}` | Sửa bản nháp, không thay đổi phiên bản đã xuất bản | hasRole('ADMIN'); STAFF cần SPA_RECEPTION | 200 |
| POST | `/api/v1/admin/spa/treatment-templates/{id}/archive` | Ngừng bán mẫu, giữ nguyên hồ sơ của khách đã mua | hasRole('ADMIN'); STAFF cần SPA_RECEPTION | 200 |
| POST | `/api/v1/admin/spa/treatment-templates/{id}/publish` | Xuất bản phiên bản mới cho các lượt mua tiếp theo | hasRole('ADMIN'); STAFF cần SPA_RECEPTION | 200 |
| POST | `/api/v1/admin/spa/treatment-templates/{id}/versions` | Tạo phiên bản nháp mới từ mẫu đã xuất bản | hasRole('ADMIN'); STAFF cần SPA_RECEPTION | 200 |
| GET | `/api/v1/admin/spa/treatments` | Xem hàng chờ tiếp nhận và hồ sơ thuộc phạm vi nhân viên | hasAnyRole('ADMIN','STAFF'); STAFF cần SPA_RECEPTION\|SPA_EXECUTION | 200 |
| GET | `/api/v1/admin/spa/treatments/report` | Báo cáo tiến độ, quyền lợi chưa dùng và tiền đã thu hoàn | hasRole('ADMIN'); STAFF cần SPA_RECEPTION\|SPA_EXECUTION | 200 |
| GET | `/api/v1/admin/spa/treatments/{id}` | Xem hồ sơ và kết quả theo quyền phụ trách | hasAnyRole('ADMIN','STAFF'); STAFF cần SPA_RECEPTION\|SPA_EXECUTION | 200 |
| POST | `/api/v1/admin/spa/treatments/{id}/assign` | Phân công người phụ trách tiếp nhận lộ trình | hasAnyRole('ADMIN','STAFF'); STAFF cần SPA_RECEPTION\|SPA_EXECUTION | 200 |
| POST | `/api/v1/admin/spa/treatments/{id}/changes` | Đề xuất phiên bản điều chỉnh, giữ nguyên lịch sử thực hiện | hasAnyRole('ADMIN','STAFF'); STAFF cần SPA_RECEPTION\|SPA_EXECUTION | 200 |
| POST | `/api/v1/admin/spa/treatments/{id}/changes/{changeId}/approve` | Quản trị duyệt điều chỉnh để khách xác nhận | hasRole('ADMIN'); STAFF cần SPA_RECEPTION\|SPA_EXECUTION | 200 |
| POST | `/api/v1/admin/spa/treatments/{id}/changes/{changeId}/reject` | Quản trị từ chối đề xuất điều chỉnh | hasRole('ADMIN'); STAFF cần SPA_RECEPTION\|SPA_EXECUTION | 200 |
| POST | `/api/v1/admin/spa/treatments/{id}/complete` | Tổng kết và kết thúc khi đủ buổi cùng kết quả bắt buộc | hasAnyRole('ADMIN','STAFF'); STAFF cần SPA_RECEPTION\|SPA_EXECUTION | 200 |
| POST | `/api/v1/admin/spa/treatments/{id}/intake` | Người phụ trách ghi nhận tiếp nhận và xác nhận kế hoạch đã mua | hasAnyRole('ADMIN','STAFF'); STAFF cần SPA_RECEPTION\|SPA_EXECUTION | 200 |
| POST | `/api/v1/admin/spa/treatments/{id}/requests` | Tạo yêu cầu vận hành cho hồ sơ điều trị | hasAnyRole('ADMIN','STAFF'); STAFF cần SPA_RECEPTION\|SPA_EXECUTION | 200 |
| POST | `/api/v1/admin/spa/treatments/{id}/requests/{requestId}/approve` | Duyệt tạm dừng, tiếp tục, dừng, sự cố hoặc gia hạn | hasRole('ADMIN'); STAFF cần SPA_RECEPTION\|SPA_EXECUTION | 200 |
| POST | `/api/v1/admin/spa/treatments/{id}/requests/{requestId}/refund-confirmation` | Xác nhận bằng chứng đã hoàn đúng số tiền được duyệt | hasRole('ADMIN'); STAFF cần SPA_RECEPTION\|SPA_EXECUTION | 200 |
| POST | `/api/v1/admin/spa/treatments/{id}/requests/{requestId}/reject` | Từ chối yêu cầu thay đổi trạng thái hồ sơ | hasRole('ADMIN'); STAFF cần SPA_RECEPTION\|SPA_EXECUTION | 200 |
| POST | `/api/v1/admin/spa/treatments/{id}/sessions/{sessionId}/result` | Ghi kết quả và hướng dẫn sau buổi, lưu lịch sử bổ sung | hasAnyRole('ADMIN','STAFF'); STAFF cần SPA_RECEPTION\|SPA_EXECUTION | 200 |
| GET | `/api/v1/admin/staff` | Lấy danh sách chuyên viên Spa | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| POST | `/api/v1/admin/staff` | Thêm hồ sơ chuyên viên Spa | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 201 |
| DELETE | `/api/v1/admin/staff/{id}` | Xóa mềm chuyên viên Spa | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| GET | `/api/v1/admin/staff/{id}` | Xem chi tiết chuyên viên Spa theo ID | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| PUT | `/api/v1/admin/staff/{id}` | Cập nhật hồ sơ chuyên viên Spa | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| GET | `/api/v1/admin/staff/{id}/schedules` | Phân trang ca làm việc của chuyên viên | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| POST | `/api/v1/admin/staff/{id}/schedules` | Thêm ca làm việc cho chuyên viên | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 201 |
| DELETE | `/api/v1/admin/staff/{id}/schedules/{scheduleId}` | Xóa mềm ca làm việc của chuyên viên | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| PUT | `/api/v1/admin/staff/{id}/schedules/{scheduleId}` | Cập nhật ca làm việc của chuyên viên | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| PUT | `/api/v1/admin/staff/{id}/skills` | Gán hoặc cập nhật kỹ năng dịch vụ | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| DELETE | `/api/v1/admin/staff/{id}/skills/{serviceId}` | Xóa mềm kỹ năng dịch vụ | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| GET | `/api/v1/appointments/admin/all` | Tra cứu lịch hẹn theo phạm vi nhân viên | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION\|SPA_EXECUTION | 200 |
| PUT | `/api/v1/appointments/admin/{id}/status` | Xác nhận, phân công hoặc chuyển trạng thái buổi Spa | hasAnyRole('ADMIN', 'STAFF'); STAFF cần SPA_RECEPTION | 200 |
| POST | `/api/v1/appointments/book` | Đặt yêu cầu lịch hẹn Spa | isAuthenticated() | 200 |
| GET | `/api/v1/appointments/checkout-policy` | Xem quy tắc chốt hóa đơn Spa | isAuthenticated() | 200 |
| GET | `/api/v1/appointments/my-appointments` | Xem danh sách lịch hẹn của tôi | isAuthenticated() | 200 |
| GET | `/api/v1/appointments/{id}` | Xem chi tiết một lịch hẹn | isAuthenticated() | 200 |
| GET | `/api/v1/appointments/{id}/available-slots` | Tra cứu /api/v1/appointments/{id}/available-slots | isAuthenticated() | 200 |
| PUT | `/api/v1/appointments/{id}/cancel` | Hủy lịch hẹn trước check-in | isAuthenticated() | 200 |
| PUT | `/api/v1/appointments/{id}/check-in` | Ghi nhận khách đến Spa | hasAnyRole('ADMIN','STAFF'); STAFF cần SPA_RECEPTION | 200 |
| GET | `/api/v1/appointments/{id}/history` | Xem lịch sử hành động của lịch hẹn | isAuthenticated() | 200 |
| GET | `/api/v1/appointments/{id}/invoice` | Xem hóa đơn và số tiền Spa còn thiếu | isAuthenticated() | 200 |
| POST | `/api/v1/appointments/{id}/invoice` | Lập hóa đơn cố định cho buổi Spa | hasAnyRole('ADMIN','STAFF'); STAFF cần SPA_RECEPTION | 200 |
| POST | `/api/v1/appointments/{id}/invoice/cash-receipts` | Ghi nhận một phiếu thu tiền mặt Spa | hasAnyRole('ADMIN','STAFF'); STAFF cần SPA_RECEPTION | 200 |
| PUT | `/api/v1/appointments/{id}/items/{itemId}/execution` | Ghi kết quả thực hiện một dịch vụ | hasAnyRole('ADMIN','STAFF'); STAFF cần SPA_EXECUTION | 200 |
| PUT | `/api/v1/appointments/{id}/reschedule` | Đổi giờ lịch hẹn trước check-in | isAuthenticated() | 200 |
| GET | `/api/v1/spa/appointments/{id}/care-summary` | Xem hồ sơ chăm sóc và versionHash hiện tại | JWT; kiểm thêm phạm vi tại service | 200 |
| POST | `/api/v1/spa/appointments/{id}/deposit-receipts` | Thực hiện /api/v1/spa/appointments/{id}/deposit-receipts | hasAnyRole('ADMIN','STAFF'); STAFF cần SPA_RECEPTION | 200 |
| POST | `/api/v1/spa/appointments/{id}/deposit-refund-confirmations` | Thực hiện /api/v1/spa/appointments/{id}/deposit-refund-confirmations | hasRole('ADMIN') | 200 |
| POST | `/api/v1/spa/appointments/{id}/fee-receipts` | Thực hiện /api/v1/spa/appointments/{id}/fee-receipts | hasAnyRole('ADMIN','STAFF'); STAFF cần SPA_RECEPTION | 200 |
| GET | `/api/v1/spa/appointments/{id}/finance` | Tra cứu /api/v1/spa/appointments/{id}/finance | isAuthenticated() | 200 |
| GET | `/api/v1/spa/appointments/{id}/follow-up-instructions` | Xem hướng dẫn sau buổi đã ghi | JWT; kiểm thêm phạm vi tại service | 200 |
| POST | `/api/v1/spa/appointments/{id}/follow-up-instructions/items/{itemId}` | Nhân viên ghi hướng dẫn cho item đã thực hiện | JWT; kiểm thêm phạm vi tại service | 201 |
| POST | `/api/v1/spa/appointments/{id}/items/{itemId}/material-usages` | Thực hiện /api/v1/spa/appointments/{id}/items/{itemId}/material-usages | hasAnyRole('ADMIN','STAFF'); STAFF cần SPA_EXECUTION | 200 |
| POST | `/api/v1/spa/appointments/{id}/items/{itemId}/material-waiver` | Thực hiện /api/v1/spa/appointments/{id}/items/{itemId}/material-waiver | hasAnyRole('ADMIN','STAFF'); STAFF cần SPA_EXECUTION | 200 |
| GET | `/api/v1/spa/appointments/{id}/materials` | Tra cứu /api/v1/spa/appointments/{id}/materials | hasAnyRole('ADMIN','STAFF') | 200 |
| GET | `/api/v1/spa/appointments/{id}/preparation` | Xem yêu cầu chuẩn bị đã chụp theo từng item | JWT; kiểm thêm phạm vi tại service | 200 |
| POST | `/api/v1/spa/appointments/{id}/preparation/forms/{requirementId}/responses` | Khách nộp và xác nhận form đã đặt | JWT; kiểm thêm phạm vi tại service | 201 |
| POST | `/api/v1/spa/appointments/{id}/preparation/items/{itemId}/acknowledge-warnings` | Người thực hiện xác nhận đã đọc cảnh báo | JWT; kiểm thêm phạm vi tại service; STAFF cần SPA_EXECUTION | 201 |
| POST | `/api/v1/spa/appointments/{id}/preparation/items/{itemId}/override` | ADMIN duyệt ngoại lệ yêu cầu chuẩn bị | JWT; kiểm thêm phạm vi tại service | 201 |
| GET | `/api/v1/spa/business-calendar` | Tra cứu /api/v1/spa/business-calendar | Công khai | 200 |
| GET | `/api/v1/spa/financial-policy` | Tra cứu /api/v1/spa/financial-policy | Công khai | 200 |
| GET | `/api/v1/spa/material-options` | Tra cứu /api/v1/spa/material-options | @staffPermissions.has('SPA_EXECUTION') or @staffPermissions.has('SPA_RECEPTION') | 200 |
| GET | `/api/v1/spa/my-treatments` | Xem các hồ sơ lộ trình của tôi | isAuthenticated() | 200 |
| GET | `/api/v1/spa/services` | Lấy danh sách dịch vụ Spa đang hoạt động | Công khai | 200 |
| GET | `/api/v1/spa/services/packages` | Lấy danh sách gói Spa có thể mua | Công khai | 200 |
| GET | `/api/v1/spa/services/slug/{slug}` | Xem chi tiết dịch vụ Spa theo slug | Công khai | 200 |
| GET | `/api/v1/spa/services/staff` | Lấy tất cả nhân viên Spa đang hoạt động | Công khai | 200 |
| GET | `/api/v1/spa/services/{id}` | Xem chi tiết dịch vụ Spa theo ID | Công khai | 200 |
| GET | `/api/v1/spa/services/{id}/available-slots` | Gợi ý giờ trống cho một dịch vụ | Công khai | 200 |
| GET | `/api/v1/spa/services/{id}/staff` | Lấy nhân viên có kỹ năng cho dịch vụ | Công khai | 200 |
| GET | `/api/v1/spa/tickets/my-active-tickets` | Xem vé của tôi còn lượt khả dụng | isAuthenticated() | 200 |
| GET | `/api/v1/spa/tickets/my-tickets` | Xem tất cả vé liệu trình của tôi | isAuthenticated() | 200 |
| POST | `/api/v1/spa/tickets/purchase` | Tạo đơn thanh toán mua gói Spa | isAuthenticated() | 200 |
| GET | `/api/v1/spa/tickets/{id}` | Xem chi tiết một vé liệu trình | isAuthenticated() | 200 |
| GET | `/api/v1/spa/tickets/{id}/movements` | Phân trang sổ lượt và điều chỉnh của vé | isAuthenticated() | 200 |
| GET | `/api/v1/spa/tickets/{id}/refunds` | Tra cứu /api/v1/spa/tickets/{id}/refunds | isAuthenticated() | 200 |
| GET | `/api/v1/spa/treatment-notifications` | Xem tối đa 100 thông báo lộ trình gần nhất của tôi | isAuthenticated() | 200 |
| PUT | `/api/v1/spa/treatment-notifications/{id}/read` | Đánh dấu đã đọc thông báo của tôi | isAuthenticated() | 200 |
| POST | `/api/v1/spa/treatment-purchases` | Tạo đơn mua lộ trình theo phiên bản và điều khoản đã chấp nhận | isAuthenticated() | 200 |
| GET | `/api/v1/spa/treatment-templates` | Xem các lộ trình đã xuất bản đang được bán | Công khai | 200 |
| GET | `/api/v1/spa/treatment-templates/{id}` | Xem phiên bản đang bán và điều khoản của lộ trình | Công khai | 200 |
| GET | `/api/v1/spa/treatments/{id}` | Xem tiến độ lộ trình trong phạm vi được phép | isAuthenticated() | 200 |
| POST | `/api/v1/spa/treatments/{id}/changes/{changeId}/accept` | Chủ hồ sơ xác nhận phiên bản kế hoạch được đề xuất | isAuthenticated() | 200 |
| POST | `/api/v1/spa/treatments/{id}/changes/{changeId}/apply` | Áp dụng điều chỉnh đã thanh toán sau khi giải quyết lịch đang giữ | isAuthenticated() | 200 |
| POST | `/api/v1/spa/treatments/{id}/changes/{changeId}/reject` | Chủ hồ sơ từ chối điều chỉnh kế hoạch | isAuthenticated() | 200 |
| POST | `/api/v1/spa/treatments/{id}/changes/{changeId}/settlement` | Tạo đơn thanh toán chênh lệch của điều chỉnh đã chấp nhận | isAuthenticated() | 200 |
| POST | `/api/v1/spa/treatments/{id}/requests` | Gửi yêu cầu tạm dừng, tiếp tục, dừng, gia hạn hoặc báo sự cố | isAuthenticated() | 200 |
| GET | `/api/v1/spa/treatments/{id}/sessions/{sessionId}/available-slots` | Tra giờ trống cho toàn bộ dịch vụ của buổi, hỗ trợ đổi lịch | isAuthenticated() | 200 |
| POST | `/api/v1/spa/treatments/{id}/sessions/{sessionId}/book` | Đặt dịch vụ còn thiếu của đúng buổi trong lộ trình đã mua | isAuthenticated() | 200 |
| GET | `/api/v1/staff/spa/treatments` | Xem hàng chờ tiếp nhận và hồ sơ thuộc phạm vi nhân viên | hasAnyRole('ADMIN','STAFF'); STAFF cần SPA_RECEPTION\|SPA_EXECUTION | 200 |
| GET | `/api/v1/staff/spa/treatments/report` | Báo cáo tiến độ, quyền lợi chưa dùng và tiền đã thu hoàn | hasRole('ADMIN'); STAFF cần SPA_RECEPTION\|SPA_EXECUTION | 200 |
| GET | `/api/v1/staff/spa/treatments/{id}` | Xem hồ sơ và kết quả theo quyền phụ trách | hasAnyRole('ADMIN','STAFF'); STAFF cần SPA_RECEPTION\|SPA_EXECUTION | 200 |
| POST | `/api/v1/staff/spa/treatments/{id}/assign` | Phân công người phụ trách tiếp nhận lộ trình | hasAnyRole('ADMIN','STAFF'); STAFF cần SPA_RECEPTION\|SPA_EXECUTION | 200 |
| POST | `/api/v1/staff/spa/treatments/{id}/changes` | Đề xuất phiên bản điều chỉnh, giữ nguyên lịch sử thực hiện | hasAnyRole('ADMIN','STAFF'); STAFF cần SPA_RECEPTION\|SPA_EXECUTION | 200 |
| POST | `/api/v1/staff/spa/treatments/{id}/changes/{changeId}/approve` | Quản trị duyệt điều chỉnh để khách xác nhận | hasRole('ADMIN'); STAFF cần SPA_RECEPTION\|SPA_EXECUTION | 200 |
| POST | `/api/v1/staff/spa/treatments/{id}/changes/{changeId}/reject` | Quản trị từ chối đề xuất điều chỉnh | hasRole('ADMIN'); STAFF cần SPA_RECEPTION\|SPA_EXECUTION | 200 |
| POST | `/api/v1/staff/spa/treatments/{id}/complete` | Tổng kết và kết thúc khi đủ buổi cùng kết quả bắt buộc | hasAnyRole('ADMIN','STAFF'); STAFF cần SPA_RECEPTION\|SPA_EXECUTION | 200 |
| POST | `/api/v1/staff/spa/treatments/{id}/intake` | Người phụ trách ghi nhận tiếp nhận và xác nhận kế hoạch đã mua | hasAnyRole('ADMIN','STAFF'); STAFF cần SPA_RECEPTION\|SPA_EXECUTION | 200 |
| POST | `/api/v1/staff/spa/treatments/{id}/requests` | Tạo yêu cầu vận hành cho hồ sơ điều trị | hasAnyRole('ADMIN','STAFF'); STAFF cần SPA_RECEPTION\|SPA_EXECUTION | 200 |
| POST | `/api/v1/staff/spa/treatments/{id}/requests/{requestId}/approve` | Duyệt tạm dừng, tiếp tục, dừng, sự cố hoặc gia hạn | hasRole('ADMIN'); STAFF cần SPA_RECEPTION\|SPA_EXECUTION | 200 |
| POST | `/api/v1/staff/spa/treatments/{id}/requests/{requestId}/refund-confirmation` | Xác nhận bằng chứng đã hoàn đúng số tiền được duyệt | hasRole('ADMIN'); STAFF cần SPA_RECEPTION\|SPA_EXECUTION | 200 |
| POST | `/api/v1/staff/spa/treatments/{id}/requests/{requestId}/reject` | Từ chối yêu cầu thay đổi trạng thái hồ sơ | hasRole('ADMIN'); STAFF cần SPA_RECEPTION\|SPA_EXECUTION | 200 |
| POST | `/api/v1/staff/spa/treatments/{id}/sessions/{sessionId}/result` | Ghi kết quả và hướng dẫn sau buổi, lưu lịch sử bổ sung | hasAnyRole('ADMIN','STAFF'); STAFF cần SPA_RECEPTION\|SPA_EXECUTION | 200 |

## Hồ sơ và chăm sóc khách hàng — 11 operations

| Method | Path | Hành động | Xác thực / guard | HTTP thành công |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/admin/crm/customers` | Tìm khách hàng, tối đa 100 kết quả | hasAnyRole('ADMIN','STAFF'); STAFF cần CRM | 200 |
| POST | `/api/v1/admin/crm/customers` | Tạo hồ sơ khách vãng lai tại quầy | hasAnyRole('ADMIN','STAFF'); STAFF cần CRM | 200 |
| GET | `/api/v1/admin/crm/customers/page` | Phân trang tìm kiếm hồ sơ khách hàng | hasAnyRole('ADMIN','STAFF'); STAFF cần CRM | 200 |
| GET | `/api/v1/admin/crm/customers/{id}` | Xem thông tin và tổng hợp hoạt động khách | hasAnyRole('ADMIN','STAFF'); STAFF cần CRM | 200 |
| GET | `/api/v1/admin/crm/customers/{id}/notes` | Xem tối đa 100 ghi chú chăm sóc mới nhất | hasAnyRole('ADMIN','STAFF'); STAFF cần CRM | 200 |
| POST | `/api/v1/admin/crm/customers/{id}/notes` | Thêm ghi chú nội bộ về chăm sóc khách | hasAnyRole('ADMIN','STAFF'); STAFF cần CRM | 200 |
| GET | `/api/v1/admin/crm/customers/{id}/notes/page` | Phân trang lịch sử ghi chú chăm sóc | hasAnyRole('ADMIN','STAFF'); STAFF cần CRM | 200 |
| DELETE | `/api/v1/admin/crm/notes/{noteId}` | Xóa mềm ghi chú chăm sóc | hasAnyRole('ADMIN','STAFF'); STAFF cần CRM | 200 |
| GET | `/api/v1/admin/crm/work-items` | Tra cứu /api/v1/admin/crm/work-items | @staffPermissions.has('CRM'); STAFF cần CRM | 200 |
| POST | `/api/v1/admin/crm/work-items` | Thực hiện /api/v1/admin/crm/work-items | @staffPermissions.has('CRM'); STAFF cần CRM | 200 |
| PUT | `/api/v1/admin/crm/work-items/{id}` | Cập nhật /api/v1/admin/crm/work-items/{id} | @staffPermissions.has('CRM'); STAFF cần CRM | 200 |

## Kho và kiểm kê — 21 operations

| Method | Path | Hành động | Xác thực / guard | HTTP thành công |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/admin/inventory/expiring-soon` | Danh sách lô có hạn từ hôm nay đến ngưỡng | hasAnyRole('ADMIN', 'STAFF'); STAFF cần INVENTORY | 200 |
| GET | `/api/v1/admin/inventory/low-stock` | Cảnh báo tồn khả dụng ở mức tối thiểu trở xuống | hasAnyRole('ADMIN', 'STAFF'); STAFF cần INVENTORY | 200 |
| GET | `/api/v1/admin/inventory/recalls` | Tra cứu /api/v1/admin/inventory/recalls | hasAnyRole('ADMIN','STAFF'); STAFF cần INVENTORY | 200 |
| POST | `/api/v1/admin/inventory/recalls` | Thực hiện /api/v1/admin/inventory/recalls | hasAnyRole('ADMIN','STAFF'); STAFF cần INVENTORY | 200 |
| POST | `/api/v1/admin/inventory/recalls/{id}/close` | Thực hiện /api/v1/admin/inventory/recalls/{id}/close | hasRole('ADMIN'); STAFF cần INVENTORY | 200 |
| GET | `/api/v1/admin/inventory/recalls/{id}/customers` | Tra cứu /api/v1/admin/inventory/recalls/{id}/customers | hasAnyRole('ADMIN','STAFF'); STAFF cần INVENTORY | 200 |
| POST | `/api/v1/admin/inventory/recalls/{id}/outreach` | Thực hiện /api/v1/admin/inventory/recalls/{id}/outreach | hasAnyRole('ADMIN','STAFF'); STAFF cần INVENTORY | 200 |
| GET | `/api/v1/admin/inventory/recalls/{id}/outreach-history` | Tra cứu /api/v1/admin/inventory/recalls/{id}/outreach-history | hasAnyRole('ADMIN','STAFF'); STAFF cần INVENTORY | 200 |
| GET | `/api/v1/admin/inventory/transactions` | Lấy lịch sử biến động kho | hasAnyRole('ADMIN', 'STAFF'); STAFF cần INVENTORY | 200 |
| POST | `/api/v1/admin/inventory/{stockId}/inspection` | Duyệt bán lại hoặc loại bỏ hàng cách ly | hasAnyRole('ADMIN', 'STAFF'); STAFF cần INVENTORY | 200 |
| GET | `/api/v1/admin/warehouses` | Lấy danh sách kho chưa xóa | hasAnyRole('ADMIN', 'STAFF'); STAFF cần INVENTORY | 200 |
| POST | `/api/v1/admin/warehouses` | Tạo kho bãi mới (Admin) | hasAnyRole('ADMIN', 'STAFF'); STAFF cần INVENTORY | 201 |
| DELETE | `/api/v1/admin/warehouses/stocks/{stockId}` | Xóa mềm lô tồn kho | hasAnyRole('ADMIN', 'STAFF'); STAFF cần INVENTORY | 200 |
| PUT | `/api/v1/admin/warehouses/stocks/{stockId}/adjustment` | Điều chỉnh số tồn bán được sau kiểm đếm | hasAnyRole('ADMIN', 'STAFF'); STAFF cần INVENTORY | 200 |
| POST | `/api/v1/admin/warehouses/stocks/{stockId}/transfer` | Transfer available stock to another warehouse | hasAnyRole('ADMIN', 'STAFF'); STAFF cần INVENTORY | 200 |
| DELETE | `/api/v1/admin/warehouses/{id}` | Xóa mềm kho khi đã hết số dư | hasAnyRole('ADMIN', 'STAFF'); STAFF cần INVENTORY | 200 |
| GET | `/api/v1/admin/warehouses/{id}` | Xem thông tin kho bãi theo ID (Admin) | hasAnyRole('ADMIN', 'STAFF'); STAFF cần INVENTORY | 200 |
| PUT | `/api/v1/admin/warehouses/{id}` | Cập nhật thông tin kho | hasAnyRole('ADMIN', 'STAFF'); STAFF cần INVENTORY | 200 |
| POST | `/api/v1/admin/warehouses/{id}/receipts` | Receive additional batch stock | hasAnyRole('ADMIN', 'STAFF'); STAFF cần INVENTORY | 200 |
| GET | `/api/v1/admin/warehouses/{id}/stocks` | Lấy danh sách tồn kho theo kho bãi (Admin) | hasAnyRole('ADMIN', 'STAFF'); STAFF cần INVENTORY | 200 |
| POST | `/api/v1/admin/warehouses/{id}/stocks` | Đặt số lượng tuyệt đối và thông tin lô | hasAnyRole('ADMIN', 'STAFF'); STAFF cần INVENTORY | 200 |

## Nhà cung cấp và mua hàng — 17 operations

| Method | Path | Hành động | Xác thực / guard | HTTP thành công |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/admin/procurement/orders` | Lấy danh sách phiếu mua hàng (lọc theo trạng thái, phân trang) | hasAnyRole('ADMIN', 'STAFF'); STAFF cần PROCUREMENT | 200 |
| POST | `/api/v1/admin/procurement/orders` | Tạo phiếu mua DRAFT | hasAnyRole('ADMIN', 'STAFF'); STAFF cần PROCUREMENT | 200 |
| GET | `/api/v1/admin/procurement/orders/{id}` | Xem chi tiết phiếu mua hàng theo ID | hasAnyRole('ADMIN', 'STAFF'); STAFF cần PROCUREMENT | 200 |
| PUT | `/api/v1/admin/procurement/orders/{id}` | Thay nội dung phiếu DRAFT | hasAnyRole('ADMIN', 'STAFF'); STAFF cần PROCUREMENT | 200 |
| PUT | `/api/v1/admin/procurement/orders/{id}/approve` | Phê duyệt DRAFT→APPROVED | hasAnyRole('ADMIN', 'STAFF'); STAFF cần PROCUREMENT | 200 |
| PUT | `/api/v1/admin/procurement/orders/{id}/cancel` | Hủy phiếu chưa nhận | hasAnyRole('ADMIN', 'STAFF'); STAFF cần PROCUREMENT | 200 |
| GET | `/api/v1/admin/procurement/orders/{id}/financials` | Tra cứu /api/v1/admin/procurement/orders/{id}/financials | hasAnyRole('ADMIN','STAFF'); STAFF cần PROCUREMENT | 200 |
| POST | `/api/v1/admin/procurement/orders/{id}/payments` | Thực hiện /api/v1/admin/procurement/orders/{id}/payments | hasRole('ADMIN'); STAFF cần PROCUREMENT | 200 |
| POST | `/api/v1/admin/procurement/orders/{id}/receipts` | Thực hiện /api/v1/admin/procurement/orders/{id}/receipts | hasAnyRole('ADMIN','STAFF'); STAFF cần PROCUREMENT | 200 |
| PUT | `/api/v1/admin/procurement/orders/{id}/receive` | Nhận toàn bộ hàng APPROVED→RECEIVED | hasAnyRole('ADMIN', 'STAFF'); STAFF cần PROCUREMENT | 200 |
| POST | `/api/v1/admin/procurement/orders/{id}/supplier-refunds` | Thực hiện /api/v1/admin/procurement/orders/{id}/supplier-refunds | hasRole('ADMIN'); STAFF cần PROCUREMENT | 200 |
| POST | `/api/v1/admin/procurement/orders/{id}/supplier-returns` | Thực hiện /api/v1/admin/procurement/orders/{id}/supplier-returns | hasAnyRole('ADMIN','STAFF'); STAFF cần PROCUREMENT | 200 |
| GET | `/api/v1/admin/procurement/suppliers` | Lấy danh sách nhà cung cấp (phân trang) | hasAnyRole('ADMIN', 'STAFF'); STAFF cần PROCUREMENT | 200 |
| POST | `/api/v1/admin/procurement/suppliers` | Tạo nhà cung cấp | hasAnyRole('ADMIN', 'STAFF'); STAFF cần PROCUREMENT | 200 |
| DELETE | `/api/v1/admin/procurement/suppliers/{id}` | Xóa nhà cung cấp | hasAnyRole('ADMIN', 'STAFF'); STAFF cần PROCUREMENT | 200 |
| GET | `/api/v1/admin/procurement/suppliers/{id}` | Xem chi tiết nhà cung cấp theo ID | hasAnyRole('ADMIN', 'STAFF'); STAFF cần PROCUREMENT | 200 |
| PUT | `/api/v1/admin/procurement/suppliers/{id}` | Thay thông tin nhà cung cấp | hasAnyRole('ADMIN', 'STAFF'); STAFF cần PROCUREMENT | 200 |

## Khuyến mãi — 6 operations

| Method | Path | Hành động | Xác thực / guard | HTTP thành công |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/admin/vouchers` | Lấy danh sách mã giảm giá Voucher (phân trang) | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| POST | `/api/v1/admin/vouchers` | Tạo voucher | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 201 |
| DELETE | `/api/v1/admin/vouchers/{id}` | Xóa mềm voucher | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| GET | `/api/v1/admin/vouchers/{id}` | Xem chi tiết Voucher theo ID | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| PUT | `/api/v1/admin/vouchers/{id}` | Thay cấu hình voucher | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| POST | `/api/v1/promotions/validate` | Kiểm tra tính hợp lệ và tính số tiền giảm của Voucher | JWT; kiểm thêm phạm vi tại service | 200 |

## Đánh giá và kiểm duyệt — 7 operations

| Method | Path | Hành động | Xác thực / guard | HTTP thành công |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/admin/reviews` | Lấy danh sách đánh giá quản trị | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| DELETE | `/api/v1/admin/reviews/{id}` | Xóa đánh giá sản phẩm (Soft delete) | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| GET | `/api/v1/admin/reviews/{id}` | Xem chi tiết đánh giá theo ID | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| PUT | `/api/v1/admin/reviews/{id}/reply` | Gửi phản hồi của Quản trị viên / CSKH cho đánh giá | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| PUT | `/api/v1/admin/reviews/{id}/status` | Kiểm duyệt trạng thái đánh giá (APPROVED hoặc REJECTED) | hasAnyRole('ADMIN', 'STAFF'); STAFF cần CATALOG | 200 |
| GET | `/api/v1/reviews` | Xem đánh giá công khai của sản phẩm | Công khai | 200 |
| POST | `/api/v1/reviews` | Gửi đánh giá từ đơn hàng đã giao | isAuthenticated() | 201 |

## Dashboard — 9 operations

| Method | Path | Hành động | Xác thực / guard | HTTP thành công |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/admin/dashboard` | Lấy tổng quan điều hành | hasAnyRole('ADMIN', 'STAFF'); STAFF cần REPORTS | 200 |
| GET | `/api/v1/admin/dashboard/expenses` | Tra cứu /api/v1/admin/dashboard/expenses | hasAnyRole('ADMIN','STAFF'); STAFF cần REPORTS | 200 |
| POST | `/api/v1/admin/dashboard/expenses` | Thực hiện /api/v1/admin/dashboard/expenses | hasRole('ADMIN'); STAFF cần REPORTS | 200 |
| GET | `/api/v1/admin/dashboard/profitability` | Tra cứu /api/v1/admin/dashboard/profitability | hasAnyRole('ADMIN','STAFF'); STAFF cần REPORTS | 200 |
| GET | `/api/v1/admin/dashboard/revenue` | Lấy xu hướng tiền vào và tiền hoàn | hasAnyRole('ADMIN', 'STAFF'); STAFF cần REPORTS | 200 |
| GET | `/api/v1/admin/dashboard/spa-financials` | Lấy số dư tài chính Spa hiện tại | hasAnyRole('ADMIN', 'STAFF'); STAFF cần REPORTS | 200 |
| GET | `/api/v1/admin/dashboard/spa-occupancy` | Lấy độ lấp lịch và thời gian phục vụ Spa | hasAnyRole('ADMIN', 'STAFF'); STAFF cần REPORTS | 200 |
| GET | `/api/v1/admin/dashboard/spa-performance` | Tra cứu /api/v1/admin/dashboard/spa-performance | hasAnyRole('ADMIN','STAFF'); STAFF cần REPORTS | 200 |
| GET | `/api/v1/admin/dashboard/top-products` | Lấy 10 sản phẩm bán chạy | hasAnyRole('ADMIN', 'STAFF'); STAFF cần REPORTS | 200 |

## Cấu hình, cảnh báo và kiểm toán — 10 operations

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
| GET | `/api/v1/orders/{id}/payment-subscription` | Cấp quyền theo dõi thanh toán 5 phút | JWT tùy chọn / phiên khách | 200 |
| GET | `/api/v1/system-configs/public` | Lấy các cấu hình công khai của hệ thống | Công khai | 200 |

## Chatbot — 6 operations

| Method | Path | Hành động | Xác thực / guard | HTTP thành công |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/chatbot/chat` | Trò chuyện tư vấn sản phẩm (Non-streaming) | Công khai | 200 |
| POST | `/api/v1/chatbot/chat/stream` | Trò chuyện tư vấn sản phẩm (Streaming SSE) | Công khai | 200 |
| GET | `/api/v1/chatbot/health` | Kiểm tra tình trạng hoạt động của dịch vụ Chatbot AI | Công khai | 200 |
| GET | `/api/v1/chatbot/openapi.json` | Tải đặc tả kỹ thuật OpenAPI JSON gốc của Chatbot | Công khai | 200 |
| POST | `/api/v1/chatbot/sync/database` | Kích hoạt đồng bộ dữ liệu từ MySQL vào kho vector RAG | hasRole('ADMIN') | 200 |
| POST | `/api/v1/chatbot/test/understand` | Kiểm tra phân tích ý định người dùng (NLU / Understanding) | hasRole('ADMIN') | 200 |

