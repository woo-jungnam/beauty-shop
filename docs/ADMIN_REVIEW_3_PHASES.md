# Báo cáo rà soát và hoàn thiện trang quản trị — 3 giai đoạn

Ngày kiểm định: 30/09/2026  
Phạm vi: `frontend` React/Vite và `backend` Spring Boot của hệ thống bán mỹ phẩm, dịch vụ chăm sóc sắc đẹp.

## Kết luận điều hành

Ba giai đoạn nền tảng của trang quản trị đã được triển khai và kiểm định ở mức sẵn sàng đưa lên môi trường staging. Các lỗi hợp đồng API ảnh hưởng trực tiếp đến đơn hàng, sản phẩm, lịch hẹn, tồn kho và dashboard đã được sửa. Hệ thống đã có thêm phân quyền theo nghiệp vụ, quản lý mua hàng/nhà cung cấp, CRM khách hàng 360°, vận hành thanh toán, nhật ký kiểm toán, cấu hình mục tiêu và cảnh báo vận hành.

Chưa nên ký duyệt production chỉ dựa trên kiểm thử tự động. Cần thực hiện UAT với dữ liệu sao lưu gần thực tế, kiểm thử cổng thanh toán/webhook, phân quyền bằng từng tài khoản nghiệp vụ và diễn tập rollback migration.

## Giai đoạn 1 — Ổn định nghiệp vụ và kiểm soát quản trị

- Sửa payload hủy đơn theo đúng hợp đồng backend (`notes`), không còn gửi trường không được xử lý.
- Sửa tạo/cập nhật sản phẩm: danh mục nhiều lựa chọn, trạng thái phát hành, slug, thương hiệu/danh mục khi sửa và các thuộc tính đặc thù mỹ phẩm như loại sản phẩm, giới tính, loại da, INCI, hướng dẫn dùng, xuất xứ, dung tích, nổi bật.
- Sửa lịch hẹn Spa: ghi chú đúng trường, phân công chuyên viên theo từng dịch vụ và giới hạn thao tác theo vai trò.
- Dashboard dùng khoảng ngày thực, số đơn đã thanh toán/chờ thanh toán và mục tiêu lấy từ cấu hình thay vì số cứng.
- Health badge không còn báo `UP` giả khi API lỗi; hiển thị trạng thái thành phần khi actuator trả dữ liệu.
- Bổ sung hash routing, điều hướng bằng tìm kiếm nhanh và menu theo quyền.
- Bổ sung phân quyền nghiệp vụ: `ORDER_STAFF`, `INVENTORY_STAFF`, `CATALOG_STAFF`, `CS_STAFF`.
- Quản trị viên có thể gán vai trò, xem phiên đăng nhập và buộc đăng xuất; thay đổi quyền làm tăng token version và vô hiệu phiên cũ.
- Refund vẫn là quyền riêng của `ADMIN`; nhân viên đơn hàng không nhìn thấy và không gọi được thao tác này.
- Bổ sung màn hình giao dịch thanh toán, số liệu đối soát, audit log và cấu hình hệ thống.
- Audit tự động bao phủ các HTTP mutation trong API quản trị, đồng thời giữ cơ chế annotation hiện hữu.

## Giai đoạn 2 — Hoàn thiện vận hành cửa hàng, kho, CRM và Spa

### Mua hàng và nhà cung cấp

- Quản lý nhà cung cấp, thông tin liên hệ, mã số thuế và trạng thái.
- Phiếu mua hàng có vòng đời `DRAFT → APPROVED → RECEIVED` hoặc `CANCELLED`.
- Kiểm tra dòng hàng, số lượng, giá vốn; mã phiếu dùng ngày và UUID rút gọn để tránh trùng khi tạo đồng thời.
- Nhận hàng cộng dồn theo kho/biến thể/lô, cập nhật giá vốn và hạn dùng, đồng thời ghi tham chiếu phiếu mua vào sổ kho.
- Không cho nhận lại phiếu đã nhận, không cho hủy phiếu đã nhận.

### Kho và tồn

- Tách nghiệp vụ nhập hàng khỏi ghi đè số lượng tuyệt đối.
- Hỗ trợ điều chỉnh và chuyển kho với khóa bi quan; kiểm tra số khả dụng, số đang giữ và số cách ly.
- Ghi ledger cho nhập hàng, điều chỉnh, chuyển ra và chuyển vào.
- Nhận hàng từ phiếu mua không còn ghi đè mức tồn tối thiểu/tối đa hoặc vị trí của lô hiện hữu.
- Không cho xóa lô còn số lượng đang giữ hoặc cách ly.

### CRM khách hàng

- Tìm kiếm khách theo tên, tài khoản, email hoặc số điện thoại.
- Hồ sơ 360° gồm hạng thành viên, điểm, số đơn, giá trị vòng đời và số lịch Spa.
- Giá trị vòng đời được tính bằng truy vấn tách biệt, tránh nhân đôi doanh thu khi khách có nhiều lịch hẹn.
- Ghi chú chăm sóc hỗ trợ tình trạng da, dị ứng, chống chỉ định và lịch theo dõi lại.

### Spa

- Điều phối trạng thái lịch hẹn và phân công chuyên viên theo kỹ năng.
- Quản lý dịch vụ Spa, chuyên viên, ca trực, vé liệu trình, gia hạn và bồi thường buổi.
- Bổ sung quản lý gói liệu trình: giá, hiệu lực, danh sách dịch vụ/số buổi, trạng thái và xóa mềm.
- Bổ sung phân trang lịch hẹn.

## Giai đoạn 3 — Vận hành, quan sát, xuất dữ liệu và trải nghiệm

- Cảnh báo vận hành cho đơn chờ xử lý, tồn thấp, cận hạn, lịch Spa và giao dịch thanh toán bất thường.
- Cấu hình mục tiêu doanh thu, mục tiêu đơn hàng và số ngày cảnh báo cận hạn từ trang quản trị.
- Xuất CSV cho dữ liệu thanh toán/audit.
- Modal có `role=dialog`, `aria-modal`, tiêu đề liên kết ARIA, focus trap, đóng bằng Escape và trả focus về phần tử gọi.
- Menu, route và API đều áp dụng RBAC; UI ẩn chức năng không có quyền nhưng backend vẫn là lớp kiểm soát quyết định.
- Migration `V25` bổ sung vai trò, cấu hình, nhà cung cấp, phiếu mua, chi tiết phiếu mua và ghi chú CRM, kèm khóa ngoại/chỉ mục/ràng buộc số lượng.

## Kết quả kiểm định kỹ thuật

- Frontend: `npm run build` thành công, 1.963 module được build.
- Frontend lint: không có error; còn warning về mẫu gọi hàm tải dữ liệu trong `useEffect` và Fast Refresh. Đây không phải lỗi build nhưng nên được xử lý dần bằng custom data hook/useCallback trong đợt refactor UI.
- Backend package: thành công trên Java 21 / Spring Boot 3.4.3.
- Backend regression gần nhất: 130 test, 0 failure, 0 error, 7 skipped theo cấu hình môi trường.
- Test nghiệp vụ bổ sung: 6 test cho điều chỉnh/chuyển kho, bảo toàn chính sách tồn khi nhận hàng và vòng đời phiếu mua; tất cả đều qua.
- Spring context và kiểm tra kiến trúc module đều qua trong full regression.

## Điều kiện trước khi production

1. Chạy migration trên bản sao dữ liệu production và diễn tập rollback/restore.
2. UAT theo từng vai trò ADMIN, ORDER_STAFF, INVENTORY_STAFF, CATALOG_STAFF và CS_STAFF.
3. Đối soát ít nhất một giao dịch thành công, thất bại, hoàn tiền và webhook lặp.
4. Kiểm kê thử một lô có reserved/quarantined, nhận phiếu mua và chuyển kho để đối chiếu ledger.
5. Kiểm tra lịch Spa trùng ca, gói liệu trình, vé hết hạn và chống chỉ định của khách.
6. Bật HTTPS, secret manager, backup định kỳ, cảnh báo giám sát và giới hạn truy cập actuator tại hạ tầng triển khai.

## Hạng mục mở rộng chưa nằm trong baseline bàn giao

- POS tại quầy với máy in hóa đơn, ca thu ngân, két tiền và đơn offline. Không nên ghép tạm vào giỏ hàng của tài khoản admin vì sẽ sai chủ sở hữu đơn và loyalty.
- 2FA/TOTP cho tài khoản quản trị, mã khôi phục và quy trình reset có kiểm soát.
- Nhận hàng từng phần, trả hàng nhà cung cấp và công nợ phải trả.
- Dashboard lợi nhuận gộp/COGS, năng suất chuyên viên, tỷ lệ quay lại và hiệu quả chiến dịch.
- Browser E2E, kiểm thử tải và quét bảo mật DAST trước production.

Các hạng mục trên cần được thiết kế như phần mở rộng riêng để không làm sai mô hình đơn hàng, thanh toán và định danh hiện tại.
