# BeautyShop Admin Atelier

Giao diện admin được thiết kế riêng cho BeautyShop, sử dụng React và Lucide hiện có. Các hình trang trí được dựng bằng CSS/SVG, không phụ thuộc template hay thư viện animation mới.

## Trải nghiệm

- Sidebar tím mận, nền lavender sáng, các điểm nhấn violet, rose, teal và amber.
- Sidebar thu gọn trên desktop; menu dạng drawer trên mobile, có khóa focus và hỗ trợ Escape.
- Tìm phân hệ bằng Ctrl/Cmd + K, hỗ trợ tiếng Việt không dấu, phím mũi tên và Enter. Kết quả tuân theo quyền của tài khoản.
- Hiệu ứng vào trang, hover/press, sheen trên nút, skeleton shimmer, biểu đồ SVG, bộ đếm số và modal vào/ra.
- Nút giảm chuyển động lưu lựa chọn ở localStorage; luôn tôn trọng `prefers-reduced-motion` của thiết bị.
- Thông báo dẫn đến đúng phân hệ admin. Trạng thái kết nối lấy từ API, không hiển thị thành công giả khi máy chủ không phản hồi.

## Cấu trúc

`src/app/adminNavigation.js` dùng chung danh sách phân hệ và quyền cho shell, sidebar và tìm nhanh. Theme nằm trong `src/app/styles/admin-shell.css`; các thành phần trong `admin-ui.css`; bố cục form trong `admin-page-layout.css`; dashboard và đăng nhập có stylesheet riêng. Style được giới hạn trong `.admin-shell` và `.admin-auth` để giao diện khách hàng tiếp tục sử dụng theme của mình.

Dashboard tiếp tục dùng các API hiện có. Số liệu tiền vào, hoàn tiền, đơn hàng và lịch spa giữ đúng ý nghĩa của dữ liệu backend; dữ liệu lỗi được phân biệt với giá trị bằng không.

Các phân hệ admin và cửa hàng tải bằng `React.lazy`/`Suspense`. Biểu đồ theo dõi kích thước thực tế bằng `ResizeObserver` để nhãn và tooltip giữ đúng tỷ lệ khi thu gọn sidebar hoặc đổi kích thước màn hình.

## Kiểm tra

```sh
npm run build
npm run lint
npx playwright test e2e/admin-atelier.spec.js
```

Browser tests mock API để kiểm tra giao diện độc lập với cơ sở dữ liệu: phân quyền, điều hướng, tìm nhanh, responsive, focus modal/drawer, thông báo, trạng thái lỗi và reduced motion. Mock chỉ tồn tại trong bài kiểm tra; giao diện vận hành lấy dữ liệu thật từ API.

Ảnh giao diện sau kiểm tra được lưu trong `artifacts/`. Đường dẫn admin: `/#/admin/dashboard`; đăng nhập: `/#/admin/login`.
