# Mobile BeautyShop

Chạy các lệnh sau trong thư mục `mobile`.

## API

API mặc định: `https://api.dermascan.world`. Base URL không gồm đường dẫn Swagger hoặc `/api/v1`; các endpoint đã có prefix riêng.

```powershell
flutter run --dart-define-from-file=env.example.json
```

Đổi sang backend local trên Android emulator:

```powershell
flutter run --dart-define=BACKEND_URL=http://10.0.2.2:8080
```

Flutter đọc `BACKEND_URL` qua `String.fromEnvironment` tại build time, không tự đọc `.env`. URL tùy chỉnh đã lưu bằng `ApiConstants.setCustomBaseUrl` được ưu tiên hơn cấu hình build.

## Kiểm tra

```powershell
flutter analyze --no-fatal-infos
flutter test
flutter build apk --debug
```

Voucher được kiểm tra qua `POST /api/v1/promotions/validate` với `code` và `orderAmount`, yêu cầu đăng nhập. Nếu server chưa cung cấp endpoint hoặc xác thực lỗi, app không áp dụng giảm giá. Chính sách vận chuyển lấy từ `/api/v1/system-configs/public`; thiếu cấu hình thì phí và tổng dự kiến được hiển thị là chưa xác định.

Danh sách ưu đãi dùng bộ lọc `onSale=true`. Backend chưa có phiên Flash Sale, thời điểm kết thúc hay tỷ lệ đã bán nên giao diện không hiển thị các số liệu đó. Checkout backend hiện xử lý toàn bộ giỏ, vì vậy app yêu cầu chọn tất cả sản phẩm trước khi đặt đơn. Tổng hiển thị trước đặt hàng là dự kiến; tổng xác nhận lấy từ phản hồi tạo đơn.
