# BÁO CÁO TRIỂN KHAI GIAO DIỆN & NGHIỆP VỤ NHÂN VIÊN LỄ TÂN / TIẾP ĐÓN SPA

**Ngày thực hiện:** 07/10/2026  
**Dự án:** BeautyShop - Hệ thống Dược Mỹ Phẩm & Clinic Spa  
**Đối tượng:** Vai trò Nhân viên tiếp đón khách / Lễ tân Spa (`ROLE_SPA_RECEPTION`, `ROLE_STAFF`) chịu sự giám sát từ Quản trị viên (`ROLE_ADMIN`).

---

## I. MỤC TIÊU & ĐỀ XUẤT NGHIỆP VỤ CỦA NHÂN VIÊN TIẾP ĐÓN SPA

Trước đây, hệ thống chỉ có giao diện tổng quản trị dành cho Quản trị viên (`Admin`). Nhân viên Spa khi đăng nhập phải thấy chung các tab cấu hình hệ thống (nhập hàng, kho, doanh thu, phân quyền tài khoản), gây rối mắt và không đáp ứng đúng tốc độ xử lý nghiệp vụ tại quầy tiếp đón thực tế.

Do đó, hệ thống được nâng cấp để tạo **Không gian làm việc Lễ tân & Quầy Spa chuyên biệt (`SpaReceptionPage.jsx`)** với 5 nghiệp vụ cốt lõi:
1. **Tiếp đón & Check-in 1 chạm:** Quét mã/tìm kiếm khách hẹn và ghi nhận khách đã đến sảnh (`CHECKED_IN`).
2. **Điều phối Kỹ thuật viên & Quản lý ca:** Xác nhận lịch hẹn chờ duyệt (`PENDING`), phân công Bác sĩ/Kỹ thuật viên phù hợp.
3. **Theo dõi tiến trình dịch vụ:** Bắt đầu ca điều trị (`IN_PROGRESS`), ghi nhận hoàn tất dịch vụ (`COMPLETED`).
4. **Thu ngân tại quầy & Xuất hoá đơn (POS Checkout):** Tạo hoá đơn buổi lẻ, tính tiền thừa cho khách trả tiền mặt (`CASH`), hoặc phát sinh mã QR chuyển khoản VietQR.
5. **Tiếp đón khách vãng lai (Walk-in Booking):** Tạo nhanh lịch hẹn cho khách đến trực tiếp tại quầy hoặc gọi hotline, hỗ trợ tự động check-in ngay.
6. **Xử lý sự cố lịch hẹn:** Dời giờ hẹn (`Reschedule`) khi khách trễ ca, hoặc đánh dấu vắng mặt (`NO_SHOW`) có ghi nhận lý do.

---

## II. CHI TIẾT CÁC THAY ĐỔI & TRIỂN KHAI TRONG SOURCE CODE

### 1. Phân quyền & Định tuyến cổng tiếp đón độc lập (`AuthProvider.jsx` & `App.jsx`)
- **`frontend/src/app/providers/AuthProvider.jsx`**:
  - Bổ sung `ROLE_SPA_RECEPTION`, `ROLE_SPA_THERAPIST` vào danh sách `operationalRoles` và biến `isOperator`.
  - Cung cấp cờ tiện ích `isSpaReception`: xác định tài khoản chuyên viên/lễ tân quầy.
- **`frontend/src/app/App.jsx`**:
  - Định tuyến phân quyền độc lập 3 Portal:
    1. **Khách hàng (`CustomerApp`)**: Cửa hàng thương mại điện tử & đặt lịch online.
    2. **Quản trị viên (`AdminShell`)**: Dành riêng cho `ROLE_ADMIN` quản lý toàn bộ hệ sinh thái.
    3. **Quầy Lễ Tân & Tiếp Đón Spa (`SpaStaffApp`)**: Cổng làm việc POS độc lập dành cho nhân viên (`ROLE_SPA_RECEPTION`, `ROLE_STAFF`).
  - Khi nhân viên Spa đăng nhập (ví dụ tài khoản `staff`, `drthao`, `ktvlan`), hệ thống tự động đưa thẳng vào **`SpaStaffApp`** với giao diện tiếp đón độc lập 100%, hoàn toàn không hiển thị Sidebar Admin hay giao diện quản trị phức tạp.
  - Quản trị viên (`ROLE_ADMIN`) có thể chuyển đổi linh hoạt giữa Admin Console và Reception Portal bằng nút công tắc chuyển đổi trên thanh Header.

### 2. Xây dựng Cổng làm việc chuyên biệt `SpaStaffApp.jsx`
- **Đường dẫn thư mục:** `frontend/src/staff/SpaStaffApp.jsx` & `frontend/src/staff/styles/staff-portal.css`.
- **Thiết kế giao diện:**
  - Header mang phong cách Quầy Lễ Tân Thẩm Mỹ Viện cao cấp (Luxury Clinic POS Bar): Logo Spa, Đồng hồ số điện tử đếm giây thực tế, Nhãn ca làm việc (Ca sáng / Ca chiều), Thông tin nhân viên trực quầy và nút Đăng xuất an toàn.
  - Thanh tab nghiệp vụ lễ tân (5 phân hệ làm việc chuyên sâu):
    - **Tab 1: Sảnh Tiếp Đón & Check-in (`queue`):** Hàng đợi khách hẹn hôm nay, bộ lọc ca trực, nút Check-in 1 chạm, phân công KTV, kích hoạt vào phòng làm và hoàn tất dịch vụ.
    - **Tab 2: Thu Ngân & Hoá Đơn POS (`pos`):** Máy tính tiền quầy, xử lý tiền mặt (tính tiền thối cho khách), render mã QR VietQR SePay động để khách quét app ngân hàng thanh toán tại chỗ, in phiếu thu điện tử.
    - **Tab 3: Tiếp Nhận Khách Vãng Lai & Hotline (`walk-in`):** Form đặt lịch nhanh cho khách bước vào quầy hoặc gọi điện hotline, hỗ trợ tự động check-in ngay.
    - **Tab 4: Tra Cứu Vé Liệu Trình Khách Hàng (`tickets`):** Tra cứu số buổi còn lại trong thẻ theo SĐT/Tên, hạn sử dụng vé điện tử.
    - **Tab 5: Sơ Đồ Phòng Khám & KTV (`resources`):** Theo dõi tình trạng phòng/giường trị liệu (Sẵn sàng tiếp nhận / Tạm dừng bảo trì) và danh sách chuyên viên trong ca trực.

### 3. Tích hợp 4 Modal nghiệp vụ tại quầy
- **Modal Tiếp đón khách vãng lai (Walk-in Booking):**
  - Nhập họ tên, SĐT, chọn dịch vụ, KTV, ngày giờ và checkbox *"Tự động ghi nhận Check-in khách đã có mặt tại sảnh"*.
  - Gọi API `POST /api/v1/appointments/book`, tự động kích hoạt check-in ngay.
- **Modal Thu ngân tại quầy & Xuất hoá đơn (Cashier Checkout):**
  - Tự động lấy các dịch vụ thực hiện không dùng vé để tạo hoá đơn `POST /api/v1/appointments/{id}/invoice`.
  - Hỗ trợ thu tiền mặt `CASH` (`POST /api/v1/appointments/{id}/invoice/cash-receipts`), tự động tính tiền thối lại cho khách.
  - Hỗ trợ chuyển khoản `BANK` với mã VietQR SePay.
- **Modal Phân công Kỹ thuật viên (Staff Assignment):**
  - Chọn KTV có chuyên môn phù hợp để xác nhận lịch cho khách.
- **Modal Dời giờ hẹn (Reschedule):**
  - Chọn ngày và giờ hẹn mới theo yêu cầu của khách gọi điện hoặc đổi ca.

---

## III. TÀI KHOẢN TRẢI NGHIỆM MẪU

| Tên tài khoản | Mật khẩu | Vai trò | Quyền hạn hiển thị |
| :--- | :--- | :--- | :--- |
| `staff` | `123456` | `ROLE_STAFF` | Quầy Lễ Tân Spa (`spa-reception`) & Lịch hẹn Spa (`spa`) |
| `drthao` | `123456` | `ROLE_STAFF` | Quầy Lễ Tân Spa & Lịch hẹn kỹ thuật viên |
| `ktvlan` | `123456` | `ROLE_STAFF` | Quầy Lễ Tân Spa & Lịch hẹn kỹ thuật viên |
| `admin` | `admin123` | `ROLE_ADMIN` | Toàn quyền toàn bộ hệ thống (kèm cả quầy lễ tân) |
