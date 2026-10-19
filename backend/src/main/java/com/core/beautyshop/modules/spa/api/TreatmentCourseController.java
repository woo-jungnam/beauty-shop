package com.core.beautyshop.modules.spa.api;

import com.core.beautyshop.modules.spa.application.service.*;
import com.core.beautyshop.modules.spa.application.dto.request.BookAppointmentRequest;
import com.core.beautyshop.modules.spa.application.dto.response.AppointmentResponse;
import com.core.beautyshop.modules.order.api.dto.SpaPackageOrderResult;
import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.shared.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.Operation;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import static com.core.beautyshop.modules.spa.application.service.TreatmentContracts.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/spa")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Treatment courses", description = "Published plans, purchases, patient-owned progress and customer confirmations")
public class TreatmentCourseController {
    private final TreatmentCourseService treatments;
    private final AppointmentService appointments;

    @Operation(summary = "Xem các lộ trình đã xuất bản đang được bán")
    @GetMapping("/treatment-templates") public ApiResponse<List<TemplateView>> templates() { return ApiResponse.success(treatments.templates(false)); }
    @Operation(summary = "Xem phiên bản đang bán và điều khoản của lộ trình")
    @GetMapping("/treatment-templates/{id}") public ApiResponse<TemplateView> template(@PathVariable Long id) { return ApiResponse.success(treatments.template(id, false)); }
    @PreAuthorize("isAuthenticated()") @Operation(summary = "Tạo đơn mua lộ trình theo phiên bản và điều khoản đã chấp nhận")
    @PostMapping("/treatment-purchases") public ApiResponse<SpaPackageOrderResult> purchase(@RequestBody PurchaseCommand command,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        if (command == null) throw new BusinessException("Thông tin mua lộ trình là bắt buộc");
        PurchaseCommand actual = key == null ? command : new PurchaseCommand(command.templateId(), command.versionId(), command.acceptedTerms(), key, command.notes());
        return ApiResponse.success(treatments.purchase(actual));
    }
    @PreAuthorize("isAuthenticated()") @Operation(summary = "Xem các hồ sơ lộ trình của tôi")
    @GetMapping("/my-treatments") public ApiResponse<List<CourseView>> mine() { return ApiResponse.success(treatments.myCourses()); }
    @PreAuthorize("isAuthenticated()") @Operation(summary = "Xem tiến độ lộ trình trong phạm vi được phép")
    @GetMapping("/treatments/{id}") public ApiResponse<CourseView> detail(@PathVariable Long id) { return ApiResponse.success(treatments.course(id)); }
    @PreAuthorize("isAuthenticated()") @Operation(summary = "Chủ hồ sơ xác nhận phiên bản kế hoạch được đề xuất")
    @PostMapping("/treatments/{id}/changes/{changeId}/accept") public ApiResponse<CourseView> accept(@PathVariable Long id, @PathVariable Long changeId, @RequestBody DecisionCommand command) { return ApiResponse.success(treatments.customerChange(id, changeId, command, true)); }
    @PreAuthorize("isAuthenticated()") @Operation(summary = "Chủ hồ sơ từ chối điều chỉnh kế hoạch")
    @PostMapping("/treatments/{id}/changes/{changeId}/reject") public ApiResponse<CourseView> reject(@PathVariable Long id, @PathVariable Long changeId, @RequestBody DecisionCommand command) { return ApiResponse.success(treatments.customerChange(id, changeId, command, false)); }
    @PreAuthorize("isAuthenticated()") @Operation(summary = "Tạo đơn thanh toán chênh lệch của điều chỉnh đã chấp nhận")
    @PostMapping("/treatments/{id}/changes/{changeId}/settlement") public ApiResponse<SpaPackageOrderResult> settlement(@PathVariable Long id, @PathVariable Long changeId) { return ApiResponse.success(treatments.settlement(id, changeId)); }
    @PreAuthorize("isAuthenticated()") @Operation(summary = "Áp dụng điều chỉnh đã thanh toán sau khi giải quyết lịch đang giữ")
    @PostMapping("/treatments/{id}/changes/{changeId}/apply") public ApiResponse<CourseView> apply(@PathVariable Long id, @PathVariable Long changeId) { return ApiResponse.success(treatments.applySettledChange(id, changeId)); }
    @PreAuthorize("isAuthenticated()") @Operation(summary = "Gửi yêu cầu tạm dừng, tiếp tục, dừng, gia hạn hoặc báo sự cố")
    @PostMapping("/treatments/{id}/requests") public ApiResponse<CourseView> request(@PathVariable Long id, @RequestBody RequestCommand command) { return ApiResponse.success(treatments.request(id, command)); }
    @PreAuthorize("isAuthenticated()") @Operation(summary = "Tra giờ trống cho toàn bộ dịch vụ của buổi, hỗ trợ đổi lịch")
    @GetMapping("/treatments/{id}/sessions/{sessionId}/available-slots") public ApiResponse<List<String>> slots(@PathVariable Long id, @PathVariable Long sessionId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, @RequestParam(required = false) Long staffId,
            @RequestParam(required = false) Long appointmentId) { return ApiResponse.success(treatments.availableSlots(id, sessionId, date, staffId, appointmentId)); }
    @PreAuthorize("isAuthenticated()") @Operation(summary = "Đặt dịch vụ còn thiếu của đúng buổi trong lộ trình đã mua")
    @PostMapping("/treatments/{id}/sessions/{sessionId}/book") public ApiResponse<AppointmentResponse> book(@PathVariable Long id, @PathVariable Long sessionId, @Valid @RequestBody BookAppointmentRequest command) {
        CourseView course = treatments.course(id);
        SessionView session = course.sessions().stream().filter(s -> Objects.equals(s.id(), sessionId)).findFirst().orElseThrow(() -> new BusinessException("Session does not belong to this treatment"));
        if (command.getItems() == null || command.getItems().stream().anyMatch(i -> i == null || session.items().stream().noneMatch(allowed -> Objects.equals(allowed.id(), i.getTreatmentSessionItemId()))))
            throw new BusinessException("Every booked item must belong to the selected treatment session");
        command.setTargetUserId(course.userId());
        return ApiResponse.success(appointments.bookAppointment(command));
    }
}
