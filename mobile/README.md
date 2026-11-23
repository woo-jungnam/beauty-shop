# Mobile BeautyShop

Chạy các lệnh sau trong thư mục `mobile`.

## API

API mặc định là `https://api.dermascan.world`. Base URL chỉ gồm giao thức, hostname và cổng tùy chọn.

```powershell
flutter run
```

Đổi sang backend local trên Android emulator:

```powershell
flutter run --dart-define=BACKEND_URL=http://10.0.2.2:8080
```

Flutter đọc `BACKEND_URL` khi build qua `--dart-define` hoặc `--dart-define-from-file`. Trên điện thoại Android thật cắm USB, nếu backend đang chạy trên máy tính ở cổng 8080, chạy `adb reverse tcp:8080 tcp:8080` trước khi build debug với `BACKEND_URL=http://localhost:8080`.

## Kiểm tra

```powershell
flutter analyze --no-fatal-infos
flutter test
flutter build apk --debug
```

Voucher được kiểm tra qua `POST /api/v1/promotions/validate` với `code` và `orderAmount`, yêu cầu đăng nhập. Nếu server chưa cung cấp endpoint hoặc xác thực lỗi, app không áp dụng giảm giá. Chính sách vận chuyển lấy từ `/api/v1/system-configs/public`; thiếu cấu hình thì phí và tổng dự kiến được hiển thị là chưa xác định.

Danh sách ưu đãi dùng bộ lọc `onSale=true`. Backend chưa có phiên Flash Sale, thời điểm kết thúc hay tỷ lệ đã bán nên giao diện không hiển thị các số liệu đó. Checkout backend hiện xử lý toàn bộ giỏ, vì vậy app yêu cầu chọn tất cả sản phẩm trước khi đặt đơn. Tổng hiển thị trước đặt hàng là dự kiến; tổng xác nhận lấy từ phản hồi tạo đơn.

## Danh muc dia chi giao hang

The bundled `assets/vietnam-addresses.json` mirrors the storefront fallback.
Source: [tinhthanhpho.com](https://www.tinhthanhpho.com/api-docs), dataset
2026.10.8, [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
Checkout also refreshes the province and ward lists from that API when available.
