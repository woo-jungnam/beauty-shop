# Quản lý giường, phòng và thiết bị Spa

Backend đã bổ sung quản lý tài nguyên theo migration `V34__spa_facility_resources.sql`. Dịch vụ có thể yêu cầu đồng thời nhiều loại, ví dụ một giường (`BED`) và một máy (`MACHINE`). Mỗi tài nguyên có loại, capacity dương, trạng thái hoạt động và các khoảng ngừng phục vụ/bảo trì.

Yêu cầu tài nguyên được cấu hình **opt-in**. Dịch vụ chưa có cấu hình tiếp tục hoạt động như trước; migration không tự gán giường/máy cho dữ liệu cũ. Khi đặt lịch mới, backend lưu bản sao yêu cầu vào từng appointment item. Việc sửa yêu cầu catalog sau đó không thay yêu cầu của lịch đã tạo.

## API quản trị

Tất cả endpoint trong bảng yêu cầu role `ADMIN`.

| Hành động | Endpoint |
| --- | --- |
| Danh sách/chi tiết | `GET /api/v1/admin/spa/facilities`, `GET /api/v1/admin/spa/facilities/{id}` |
| Tạo/sửa/xóa mềm | `POST /api/v1/admin/spa/facilities`, `PUT /api/v1/admin/spa/facilities/{id}`, `DELETE /api/v1/admin/spa/facilities/{id}` |
| Danh sách khoảng ngừng phục vụ | `GET /api/v1/admin/spa/facilities/{id}/blocks` |
| Tạo/sửa/xóa khoảng ngừng phục vụ | `POST /api/v1/admin/spa/facilities/{id}/blocks`, `PUT` hoặc `DELETE /api/v1/admin/spa/facilities/{id}/blocks/{blockId}` |
| Xem/thay toàn bộ yêu cầu dịch vụ | `GET` hoặc `PUT /api/v1/admin/spa/services/{id}/resource-requirements` |

`id` của facility và `blockId` là ID database tương ứng; block phải thuộc facility trong đường dẫn. Danh sách resource/block không phân trang. POST tạo facility/block trả HTTP 201; GET/PUT/DELETE trả HTTP 200, DELETE trả `ApiResponse` có data null. Không dùng `Idempotency-Key` cho các thao tác quản trị này.

Body tạo tài nguyên:

```json
{"name":"Giường 1","description":"Phòng chăm sóc da","type":"BED","capacity":1,"active":true}
```

`type` là mã tối đa 40 ký tự, được chuẩn hóa sang chữ hoa; cho phép chữ ASCII, số và `_`, bắt đầu bằng chữ. Mặc định tài nguyên mới dùng `GENERAL`, capacity `1`, active `true`. Khi cập nhật, type/capacity/active không truyền sẽ giữ giá trị hiện tại. Tên là bắt buộc, tối đa 100 ký tự; description tối đa 250 ký tự.

Body thay yêu cầu dịch vụ:

```json
[{"type":"BED","units":1},{"type":"MACHINE","units":1}]
```

Đây là thay toàn bộ danh sách; `[]` bỏ ràng buộc cho **lịch mới**. Không chấp nhận type trùng hoặc units không dương. Cấu hình không tự tạo tài nguyên: nếu chưa có đủ tài nguyên cùng loại đang hoạt động, slot tương ứng không khả dụng và đặt lịch bị từ chối.

`GET /api/v1/spa/services/{id}/available-slots?date=2026-10-05&staffId=1` là API công khai gợi ý giờ, kết quả trong `ApiResponse.data` dạng `["10:00","10:30"]`. `staffId` là ID hồ sơ Staff, không phải User.id; có thể bỏ để tìm ít nhất một staff phù hợp. Gợi ý theo ca/kỹ năng, planned interval và resource hiện tại không giữ chỗ; booking kiểm lại dưới khóa. Trong request `POST /api/v1/appointments/book`, `items[].facilityId` là lựa chọn resource cụ thể tùy chọn, khác `serviceId` hoặc `staffId`.

Body tạo khoảng bảo trì:

```json
{"startAt":"2026-10-05T12:00:00","endAt":"2026-10-05T14:00:00","reason":"Bảo trì thiết bị"}
```

Khoảng dùng ngày/giờ địa phương của Spa, cùng cách hiểu Asia/Ho_Chi_Minh của lịch hẹn. Một block chiếm toàn bộ capacity của tài nguyên trong khoảng đó. End phải sau start; lý do bắt buộc, tối đa 500 ký tự.

## Phân bổ và bảo vệ dữ liệu

- Book, confirm/start và reschedule kiểm tài nguyên theo bản sao yêu cầu của item. Slot công khai kiểm cấu hình hiện tại để gợi ý; thao tác ghi kiểm lại dưới khóa.
- Mỗi loại có thể dùng một hoặc nhiều tài nguyên để đủ units. Ví dụ cần ba giường có thể phân bổ hai đơn vị từ một tài nguyên capacity 2 và một đơn vị từ tài nguyên khác capacity 1.
- Các dòng facility được khóa theo ID tăng dần. Query các lịch chiếm dụng là đọc thường dưới mutex đó, tránh khóa JOIN appointment ngược thứ tự với luồng đổi lịch.
- Capacity được kiểm bằng **mức sử dụng đồng thời lớn nhất** trong khoảng cần đặt. Hai lịch nối tiếp không bị cộng thành hai đơn vị chiếm suốt cả khoảng.
- Khi START từng item, backend kiểm riêng các item thực tế còn IN_PROGRESS, kể cả planned end đã qua. Dịch vụ đang chạy quá giờ vẫn chiếm capacity đến khi PERFORMED; item kế tiếp phải chờ hoặc được điều phối lại. Maintenance đang hiệu lực cũng chặn START; không thêm block bao trùm thời điểm hiện tại nếu facility còn đang phục vụ.
- JDBC đọc SQL TIME bằng Calendar UTC khớp Hibernate để giờ business không lệch khi JVM chạy ngoài UTC.
- PENDING/CONFIRMED/IN_PROGRESS giữ tài nguyên; CANCELLED/NO_SHOW/COMPLETED giải phóng. Item SKIPPED hoặc NO_SHOW cũng không giữ tài nguyên dù các item khác trong lịch còn tiếp tục.
- Field cũ `appointment_item.facility` được giữ làm tài nguyên đầu tiên để tương thích. Khi đã có allocations, occupancy dùng allocations và không cộng lại field cũ. Dữ liệu legacy chỉ có field đó được tính một đơn vị.
- `facilityId` tùy chọn trong item đặt lịch yêu cầu một tài nguyên cụ thể phù hợp. Nếu không phù hợp/không còn chỗ thì bị từ chối; backend vẫn phân bổ thêm các loại khác nếu dịch vụ cần. Khi reschedule, ưu tiên giữ tài nguyên cũ nếu còn chỗ rồi có thể chuyển sang tài nguyên khác cùng loại.
- Xóa/ngưng hoạt động/đổi loại/giảm capacity bị chặn khi có lịch đang giữ tài nguyên. Tạo hoặc sửa block bị chặn nếu khoảng mới đè lên lịch đang giữ; quản lý cần đổi/hủy lịch trước. Không tự hủy lịch hoặc tự chuyển dịch vụ.
- Đổi lịch không đủ tài nguyên thất bại toàn giao dịch, giữ giờ và allocations đã lưu trước đó.

## Kiểm chứng

Đã bổ sung `FacilitySchedulingIntegrationTest` và cùng suite trên MySQL Testcontainers qua `FacilitySchedulingMySqlIntegrationTest`. Mười trường hợp bao phủ: hai staff/một giường và giờ JDBC/JPA nhất quán; bed+machine; hủy/skip giải phóng; rollback đổi lịch; snapshot không đổi theo catalog; maintenance/retirement guards; capacity theo peak; legacy không tính hai lần; hai request cạnh tranh chỗ cuối; và actual execution kéo dài chặn START kế tiếp cho đến khi hoàn tất.

Kết quả chạy được đối chiếu ở báo cáo kiểm thử cuối của dự án. Việc có test MySQL trong mã chưa đồng nghĩa đã chạy thành công; Docker không có sẽ skip suite đó.
