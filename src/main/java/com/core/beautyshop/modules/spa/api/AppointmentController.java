package com.core.beautyshop.modules.spa.api;

import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.modules.spa.application.dto.request.BookAppointmentRequest;
import com.core.beautyshop.modules.spa.application.dto.response.AppointmentResponse;
import com.core.beautyshop.modules.spa.application.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springdoc.core.annotations.ParameterObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Lịch hẹn Spa", description = "Đặt yêu cầu, phân công, check-in và ghi kết quả từng dịch vụ; phạm vi dữ liệu theo chủ lịch và nhân viên được giao")
@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    @Operation(summary = "Đặt yêu cầu lịch hẹn Spa", description = "Yêu cầu đăng nhập; chủ lịch là tài khoản hiện tại. Tạo PENDING, chưa cam kết đã phân công nhân viên. Các mục chạy tuần tự theo thứ tự items, dùng giờ Việt Nam, giữ quota vé và tài nguyên nếu có cấu hình. Giá/tên/thời lượng/yêu cầu được lưu lúc đặt. Endpoint không hỗ trợ Idempotency-Key cho booking.")
    @PostMapping("/book")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AppointmentResponse>> bookAppointment(
            @Valid @RequestBody BookAppointmentRequest request) {
        AppointmentResponse response = appointmentService.bookAppointment(request);
        return ResponseEntity.status(201).body(ApiResponse.created(
                response,
                "Đã ghi nhận yêu cầu đặt lịch, đang chờ xác nhận"
        ));
    }

    @Operation(summary = "Xem danh sách lịch hẹn của tôi", description = "Yêu cầu đăng nhập; chỉ trả lịch chưa xóa thuộc tài khoản hiện tại. Đây là danh sách không phân trang.")
    @GetMapping("/my-appointments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getUserAppointments() {
        return ResponseEntity.ok(ApiResponse.success(
                appointmentService.getMyAppointments()
        ));
    }

    @Operation(summary = "Xem chi tiết một lịch hẹn", description = "Chủ lịch hoặc ADMIN/STAFF/SPA_RECEPTION được xem. SPA_THERAPIST chỉ được xem lịch có item phân công cho Staff.userId của tài khoản hiện tại. Không trả lịch đã xóa.")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AppointmentResponse>> getAppointmentById(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                appointmentService.getAppointmentById(id)
        ));
    }

    @Operation(summary = "Hủy lịch hẹn trước check-in", description = "Chủ lịch hoặc ADMIN/STAFF/SPA_RECEPTION. Chỉ PENDING/CONFIRMED, chưa check-in; khách tuân cutoff đã chụp lúc đặt. Nhân viên hủy cho khách khác hoặc vượt cutoff cần reason. Hoàn lượt RESERVED và giải phóng tài nguyên; không tự hoàn tiền hoặc thu phí.")
    @PutMapping("/{id}/cancel")
    @com.core.beautyshop.shared.audit.api.annotation.AuditAction(action = "CANCEL_APPOINTMENT", resourceType = "APPOINTMENT")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> cancelAppointment(
            @PathVariable Long id, @Valid @RequestBody(required=false) CancellationRequest request) {
        appointmentService.cancelAppointment(id, request == null ? null : request.reason());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Schema(name = "SpaAppointmentCancellationRequest", description = "Lý do hủy; bắt buộc khi nhân viên hủy cho người khác hoặc xử lý ngoại lệ cutoff")
    public record CancellationRequest(@Schema(description = "Lý do hủy, tối đa 500 ký tự", maxLength = 500, example = "Khách yêu cầu hủy lịch") @jakarta.validation.constraints.Size(max=500) String reason) { }

    @Operation(summary = "Tra cứu lịch hẹn theo phạm vi nhân viên", description = "ADMIN hoặc STAFF xem các lịch chưa xóa. Lọc date/status tùy chọn, phân trang từ page=0.")
    @GetMapping("/admin/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<com.core.beautyshop.shared.dto.PageResponse<AppointmentResponse>>> getAllAppointments(
            @Parameter(description = "Ngày hẹn tại Việt Nam, yyyy-MM-dd", example = "2026-10-05") @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date,
            @Parameter(description = "Lọc trạng thái lịch hẹn") @RequestParam(required = false) com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus status,
            @org.springdoc.core.annotations.ParameterObject @org.springframework.data.web.PageableDefault(size = 20) org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Page<AppointmentResponse> page = appointmentService.getAllAppointments(date, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(com.core.beautyshop.shared.dto.PageResponse.of(page)));
    }

    @Operation(summary = "Xác nhận, phân công hoặc chuyển trạng thái buổi Spa", description = "ADMIN hoặc STAFF: PENDING→CONFIRMED/CANCELLED; CONFIRMED→IN_PROGRESS/CANCELLED/NO_SHOW; IN_PROGRESS→COMPLETED. Phân công bằng staffAssignments chỉ khi trạng thái đích CONFIRMED, kể cả gửi lại cùng CONFIRMED. IN_PROGRESS cần check-in và đến giờ; COMPLETED cần mọi item PERFORMED/SKIPPED. NO_SHOW cần chưa check-in, qua start+grace và notes có lý do.")
    @PutMapping("/admin/{id}/status")
    @com.core.beautyshop.shared.audit.api.annotation.AuditAction(action = "UPDATE_APPOINTMENT_STATUS", resourceType = "APPOINTMENT")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> updateAppointmentStatus(
            @PathVariable Long id,
            @Valid @RequestBody com.core.beautyshop.modules.spa.application.dto.request.UpdateAppointmentStatusRequest request) {
        AppointmentResponse response = appointmentService.updateAppointmentStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Đổi giờ lịch hẹn trước check-in", description = "Chủ lịch hoặc ADMIN/STAFF; chỉ PENDING/CONFIRMED chưa check-in. Giữ dịch vụ, giá và thời lượng đã đặt; kiểm lại nhân viên, ca, hạn vé và tài nguyên. Khách tuân cutoff snapshot; nhân viên thao tác cho khách khác hoặc ngoài cutoff phải có notes ghi lý do. Thất bại giữ nguyên lịch và allocations.")
    @PutMapping("/{id}/reschedule")
    @com.core.beautyshop.shared.audit.api.annotation.AuditAction(action = "RESCHEDULE_APPOINTMENT", resourceType = "APPOINTMENT")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AppointmentResponse>> rescheduleAppointment(
            @PathVariable Long id,
            @Valid @RequestBody com.core.beautyshop.modules.spa.application.dto.request.RescheduleAppointmentRequest request) {
        AppointmentResponse response = appointmentService.rescheduleAppointment(id, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    @Operation(summary = "Ghi nhận khách đến Spa", description = "ADMIN hoặc STAFF; lịch phải CONFIRMED và có ngày hẹn là hôm nay theo Asia/Ho_Chi_Minh. Lưu checkedInAt/checkedInByUserId; gửi lại không tạo lần check-in mới. Check-in không tự bắt đầu buổi hoặc thu tiền.")
    @PutMapping("/{id}/check-in")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public ApiResponse<AppointmentResponse> checkIn(@PathVariable Long id) {
        return ApiResponse.success(appointmentService.checkIn(id));
    }

    @Operation(summary = "Ghi kết quả thực hiện một dịch vụ", description = "ADMIN hoặc STAFF. Buổi phải IN_PROGRESS. Chỉ PLANNED→IN_PROGRESS→PERFORMED hoặc PLANNED→SKIPPED; SKIPPED cần notes. START kiểm thứ tự item, staff/tài nguyên còn đang bận, form/ack và hạn vé theo policy. PERFORMED tiêu thụ lượt đã giữ; SKIPPED hoàn giữ.")
    @PutMapping("/{id}/items/{itemId}/execution")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public ApiResponse<AppointmentResponse> executeItem(@PathVariable Long id, @PathVariable Long itemId,
            @Valid @RequestBody com.core.beautyshop.modules.spa.application.dto.request.ExecuteAppointmentItemRequest request) {
        return ApiResponse.success(appointmentService.executeItem(id, itemId, request));
    }

    @Operation(summary = "Xem lịch sử hành động của lịch hẹn", description = "Chủ lịch, ADMIN/STAFF/SPA_RECEPTION hoặc SPA_THERAPIST được giao; phân trang page=0, size mặc định 20. Gồm trạng thái trước/sau, actor, thời điểm và lý do khi có.")
    @GetMapping("/{id}/history") @PreAuthorize("isAuthenticated()")
    public ApiResponse<com.core.beautyshop.shared.dto.PageResponse<com.core.beautyshop.modules.spa.domain.AppointmentActionHistory>> history(
            @PathVariable Long id, @ParameterObject @org.springframework.data.web.PageableDefault(size=20) org.springframework.data.domain.Pageable pageable) {
        return ApiResponse.success(com.core.beautyshop.shared.dto.PageResponse.of(appointmentService.actionHistory(id, pageable)));
    }
}
