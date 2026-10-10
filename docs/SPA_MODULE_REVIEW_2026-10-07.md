# BÁO CÁO TOÀN DIỆN VỀ KIẾN TRÚC & NGHIỆP VỤ MODULE SPA (REVIEW & REMEDIATION)

**Ngày thực hiện:** 07/10/2026  
**Dự án:** BeautyShop - Hệ thống Dược Mỹ Phẩm & Clinic Spa  
**Phạm vi rà soát:** Nghiệp vụ Backend (`backend/src/main/java/com/core/beautyshop/modules/spa`)

---

## I. TỔNG QUAN HIỆN TRẠNG KIẾN TRÚC

Module Spa được thiết kế theo mô hình hướng nghiệp vụ Domain-Driven Design (DDD):
1. **Quản lý lịch hẹn (`Appointment` & `AppointmentItem`):**
   - Hỗ trợ chuỗi dịch vụ thực hiện tuần tự trong một buổi khám/chăm sóc da.
   - Vòng đời trạng thái: `PENDING` ➔ `CONFIRMED` ➔ `IN_PROGRESS` (ràng buộc bắt buộc đã ghi nhận `checkedInAt`) ➔ `COMPLETED` ➔ Tạo hoá đơn (`SpaVisitInvoice`).
   - Từng dịch vụ (`AppointmentItem`) có trạng thái thực thi độc lập: `PLANNED` ➔ `IN_PROGRESS` ➔ `PERFORMED` / `SKIPPED` / `NO_SHOW`.
2. **Kế toán hạn mức vé liệu trình (`UserServiceTicket` & `TicketUsageService`):**
   - Sổ cái chuyển động 2 bước (Double-entry movement ledger) qua `TicketSessionMovement`.
   - Quản lý chặt 3 trạng thái hạn mức: `RESERVED` ➔ `REDEEMED` (hoặc `FORFEITED` khi no-show, `RELEASED` khi hủy).
3. **Phân bổ tài nguyên 2 tầng:**
   - Nhân viên: Ràng buộc kỹ năng (`StaffServiceSkill`), ca làm việc (`StaffSchedule`), và kiểm tra xung đột trùng giờ (`existsOverlappingAppointmentForStaff`).
   - Thiết bị & Giường phòng: Ràng buộc sức chứa (`Facility`), khóa bảo trì (`FacilityBlock`), và định mức tài nguyên (`ServiceFacilityRequirement`).

---

## II. PHÂN TÍCH GIẢ ĐỊNH CÁC TÌNH HUỐNG THỰC TẾ (STRESS-TESTING SCENARIOS)

| STT | Tình huống giả định | Hành vi trước khi sửa | Rủi ro vận hành |
| :--- | :--- | :--- | :--- |
| **1** | **Khách hàng đặt trùng giờ nhiều KTV (Spam / Double Booking)** | Hệ thống chỉ kiểm tra trùng lịch của KTV và Giường, **không kiểm tra `userId`**. Khách A có thể đặt cùng lúc 3 buổi hẹn vào 09:00 với 3 KTV khác nhau. | Khách chỉ đến 1 nơi, 2 nơi còn lại bị "bỏ trống ca", chiếm dụng KTV và phòng điều trị ảo gây thiệt hại doanh thu. |
| **2** | **Lễ tân / Quản trị viên đặt lịch hộ khách hàng (Walk-in & Hotline)** | API `POST /api/v1/appointments/book` lấy cứng `userId` từ token đăng nhập hiện tại (`SecurityUtils.getCurrentUserId()`). | Toàn bộ lịch đặt hộ qua hotline/quầy bị ghi nhận vào tên tài khoản Lễ tân. Khách hàng không xem được lịch trên App, làm sai lệch hồ sơ CRM. |
| **3** | **Tên Kỹ thuật viên bị che giấu thành ID thô** | Dữ liệu trả về `staffName` bị gán cứng chuỗi `"Staff #" + id` (ví dụ: Staff #1, Staff #2). | Khách hàng và Lễ tân không biết ai là chuyên viên/bác sĩ phụ trách chăm sóc da. |
| **4** | **Thiếu số điện thoại khách hàng trên lịch hẹn** | DTO `AppointmentResponse` chỉ chứa `userId` và `customerName`, thiếu `customerPhone`. | Khi khách trễ giờ hoặc cần xác nhận ca gấp, Lễ tân trên màn hình quản lý không có số điện thoại để bấm gọi trực tiếp. |
| **5** | **Khách làm thêm dịch vụ tại chỗ (In-session Upsell)** | Lịch hẹn sau khi tạo cố định danh sách `items`, không có API thêm dịch vụ khi buổi đang `IN_PROGRESS`. | Lễ tân phải tạo buổi hẹn riêng biệt, gây phân mảnh và không gộp được hoá đơn thanh toán. |
| **6** | **Điều chuyển KTV khẩn cấp giữa buổi** | Hệ thống chặn cập nhật `staffAssignments` nếu trạng thái khác `CONFIRMED`. Buổi đã sang `IN_PROGRESS` không cho đổi KTV của các bước sau. | Không thể linh hoạt xử lý khi KTV gặp sự cố sức khỏe hoặc ca sau yêu cầu Bác sĩ chuyên khoa cao hơn. |
| **7** | **Khách đến trễ dồn ca (Late Arrival Cascading Delay)** | Ca trước làm trễ kéo dài sang giờ ca sau của KTV, ca sau bị chặn do `hasActiveExecutionForStaff`. | Không có cơ chế cắt giảm bước điều trị hoặc cảnh báo dồn ca tự động. |
| **8** | **Thời gian dọn phòng & khử khuẩn (Turnaround Buffer)** | Ca sau có thể đặt sát sạt ca trước (0ms giãn cách). | KTV không có 10–15 phút thay ga giường, khử trùng đầu máy Aqua Peel/Laser, vi phạm quy chuẩn vô trùng y tế. |
| **9** | **Phân tách chi nhánh (Multi-Branch Isolation)** | Thực thể `Facility`, `Staff`, `Appointment` không có trường `branchId` (dùng chung một bể tài nguyên). | Giường số 1 ở Quận 1 bị trùng với Giường số 1 ở Quận 3 nếu đặt cùng giờ. |

---

## III. CÁC VẤN ĐỀ ĐÃ KHẮC PHỤC TRỰC TIẾP TRONG CODE (07/10/2026)

### 1. Bổ sung ràng buộc chặn khách hàng đặt trùng lịch (User Overlap Prevention)
- **Vị trí file:**
  - `backend/src/main/java/com/core/beautyshop/modules/spa/domain/AppointmentRepository.java`
  - `backend/src/main/java/com/core/beautyshop/modules/spa/application/service/impl/AppointmentServiceImpl.java`
- **Thay đổi:**
  - Bổ sung 2 query JPQL: `existsOverlappingAppointmentForUser` và `existsOverlappingAppointmentForUserExcluding` kiểm tra khung giờ `(startTime < :endTime AND endTime > :startTime)` với các trạng thái hoạt động (`PENDING`, `CONFIRMED`, `IN_PROGRESS`).
  - Kiểm tra tự động tại cả 2 luồng: **Đặt lịch mới (`bookAppointment`)** và **Đổi giờ lịch hẹn (`rescheduleAppointment`)**.
  - Nếu trùng lịch, hệ thống từ chối ngay lập tức và thông báo khung giờ xung đột.

### 2. Hỗ trợ Lễ tân / Quản trị viên đặt lịch hộ cho Khách hàng (`targetUserId`)
- **Vị trí file:**
  - `backend/src/main/java/com/core/beautyshop/modules/spa/application/dto/request/BookAppointmentRequest.java`
  - `backend/src/main/java/com/core/beautyshop/modules/spa/application/service/impl/AppointmentServiceImpl.java`
- **Thay đổi:**
  - Thêm thuộc tính `targetUserId` vào `BookAppointmentRequest`.
  - Phân quyền chặt chẽ: Chỉ tài khoản có quyền quản trị quầy (`ADMIN`, `STAFF`, `SPA_RECEPTION`) mới được chỉ định `targetUserId`. Người dùng thông thường gửi lên sẽ luôn bị ghi đè thành chính ID của mình.
  - Hỗ trợ lưu đúng chủ lịch hẹn thực tế cho khách vãng lai hoặc khách đặt qua hotline.

### 3. Hiển thị tên thật của Kỹ thuật viên & Bác sĩ (`staffName`)
- **Vị trí file:**
  - `backend/src/main/java/com/core/beautyshop/modules/spa/application/dto/response/AppointmentResponse.java`
  - `backend/src/main/java/com/core/beautyshop/modules/spa/application/service/impl/AppointmentServiceImpl.java`
- **Thay đổi:**
  - Xóa bỏ logic gán cứng `"Staff #" + item.getStaff().getId()`.
  - Bổ sung hàm `resolveStaffNames(appointments)` thu thập danh sách `userId` của các `Staff` được gán, truy vấn batch qua `IdentityFacade.findUserSummaries()` để lấy họ tên đầy đủ (`fullName`).
  - Khách hàng và Lễ tân giờ đây nhìn thấy tên chuyên viên chuẩn xác (ví dụ: "Nguyễn Thị Lan Anh", "Bác sĩ Minh Tuấn").

### 4. Bổ sung Số điện thoại Khách hàng (`customerPhone`) trong Lịch hẹn
- **Vị trí file:**
  - `backend/src/main/java/com/core/beautyshop/modules/spa/application/dto/response/AppointmentResponse.java`
  - `backend/src/main/java/com/core/beautyshop/modules/spa/application/service/impl/AppointmentServiceImpl.java`
- **Thay đổi:**
  - Bổ sung trường `customerPhone` vào DTO `AppointmentResponse`.
  - Tự động map số điện thoại từ `IdentityFacade` vào tất cả các endpoint tra cứu: `getAppointmentById`, `getMyAppointments`, `getAllAppointments`, `updateAppointmentStatus`. Lễ tân có thể gọi điện xác nhận hoặc chăm sóc ngay trên bảng điều khiển.

### 5. Sửa lỗi "Không còn khung giờ trống khả dụng" (Missing Staff Schedules & Fallback)
- **Vị trí file:**
  - `backend/src/main/resources/db/migration/V42__seed_staff_schedules.sql`
  - `backend/src/main/java/com/core/beautyshop/modules/spa/domain/StaffScheduleRepository.java`
  - `backend/src/main/java/com/core/beautyshop/modules/spa/application/service/impl/BeautyServiceServiceImpl.java`
  - `backend/src/main/java/com/core/beautyshop/modules/spa/application/service/impl/AppointmentServiceImpl.java`
- **Nguyên nhân lỗi:**
  - Bảng `staff_schedules` chưa từng được khởi tạo dữ liệu mẫu (seed data) trong bất kỳ file migration nào trước đây.
  - Hàm `getAvailableSlots` kiểm tra cứng ràng buộc `schedules.coversWorkingInterval(...)`. Vì không có ca trực nào được xếp cho ngày được chọn (ví dụ 2026-10-08), điều kiện luôn trả về `false`, khiến API trả về danh sách giờ trống rỗng `[]` và client thông báo không còn giờ trống cho mọi ngày trong tương lai.
- **Giải pháp xử lý:**
  - **Tạo migration V42:** Tự động sinh ca trực tiêu chuẩn (`08:00:00 - 20:00:00`, `SCHEDULED`) cho toàn bộ chuyên viên còn hoạt động trong 180 ngày tiếp theo (từ 10/2026 đến 04/2027).
  - **Thêm cơ chế Fallback thông minh:** Nếu ngày đó quản trị viên chưa xếp ca riêng biệt (`!schedules.hasScheduleOnDate`), chuyên viên hoạt động được mặc định phục vụ trong khung giờ mở cửa của Spa (`08:00 - 20:00`), loại bỏ tình trạng hệ thống bị "tê liệt" hoàn toàn lịch hẹn nếu chưa kịp xếp ca thủ công.

---

## IV. LỘ TRÌNH ĐỀ XUẤT CHO CÁC GIAI ĐOẠN TIẾP THEO

1. **Giai đoạn mở rộng Multi-Branch (Nhiều chi nhánh):**
   - Thêm bảng `branches` (`id`, `name`, `address`, `phone`, `operating_hours`).
   - Thêm khóa ngoại `branch_id` vào `facilities`, `staffs`, và `appointments`.
   - Phân lập hoàn toàn tài nguyên phòng và ca trực nhân viên theo từng chi nhánh.
2. **Giai đoạn hỗ trợ Dịch vụ phát sinh tại chỗ (In-Session Add-on):**
   - Bổ sung endpoint `POST /api/v1/appointments/{id}/items` dành riêng cho Lễ tân khi lịch hẹn đang ở trạng thái `IN_PROGRESS`.
   - Cho phép chọn kỹ thuật viên rảnh và tự động cộng dồn vào `SpaVisitCharge` lúc thanh toán checkout.
3. **Giai đoạn Sàng lọc Y khoa & Chữ ký cam kết (Medical Screening & Consent Waiver):**
   - Bổ sung bảng khảo sát tiền sử da (dị ứng hoạt chất, mang thai, lăn kim gần đây) khi đặt các dịch vụ treatment cao cấp.
   - Thêm trường lưu ảnh chữ ký điện tử / xác nhận miễn trừ trách nhiệm trước khi kỹ thuật viên nhấn bắt đầu thực hiện liệu trình.
