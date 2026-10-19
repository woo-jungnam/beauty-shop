package com.core.beautyshop.modules.spa.api;
import com.core.beautyshop.modules.spa.application.service.TreatmentCourseService;
import com.core.beautyshop.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import static com.core.beautyshop.modules.spa.application.service.TreatmentContracts.*;

@RestController @RequiredArgsConstructor
@RequestMapping({"/api/v1/staff/spa/treatments", "/api/v1/admin/spa/treatments"})
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Treatment operations", description = "Assigned clinical staff and administrators; financial approval remains administrator-only")
public class OperationalTreatmentController {
    private final TreatmentCourseService treatments;
    @Operation(summary = "Xem hàng chờ tiếp nhận và hồ sơ thuộc phạm vi nhân viên")
    @GetMapping public ApiResponse<List<CourseView>> list() { return ApiResponse.success(treatments.operationalCourses()); }
    @Operation(summary = "Xem hồ sơ và kết quả theo quyền phụ trách")
    @GetMapping("/{id:[0-9]+}") public ApiResponse<CourseView> detail(@PathVariable Long id) { return ApiResponse.success(treatments.course(id)); }
    @PreAuthorize("hasRole('ADMIN')") @Operation(summary = "Báo cáo tiến độ, quyền lợi chưa dùng và tiền đã thu hoàn")
    @GetMapping("/report") public ApiResponse<Report> report() { return ApiResponse.success(treatments.report()); }
    @Operation(summary = "Phân công người phụ trách tiếp nhận lộ trình")
    @PostMapping("/{id}/assign") public ApiResponse<CourseView> assign(@PathVariable Long id, @RequestBody AssignCommand c) { return ApiResponse.success(treatments.assign(id, c)); }
    @Operation(summary = "Người phụ trách ghi nhận tiếp nhận và xác nhận kế hoạch đã mua")
    @PostMapping("/{id}/intake") public ApiResponse<CourseView> intake(@PathVariable Long id, @RequestBody IntakeCommand c) { return ApiResponse.success(treatments.intake(id, c)); }
    @Operation(summary = "Đề xuất phiên bản điều chỉnh, giữ nguyên lịch sử thực hiện")
    @PostMapping("/{id}/changes") public ApiResponse<CourseView> change(@PathVariable Long id, @RequestBody ChangeCommand c) { return ApiResponse.success(treatments.proposeChange(id, c)); }
    @PreAuthorize("hasRole('ADMIN')") @Operation(summary = "Quản trị duyệt điều chỉnh để khách xác nhận")
    @PostMapping("/{id}/changes/{changeId}/approve") public ApiResponse<CourseView> approve(@PathVariable Long id, @PathVariable Long changeId, @RequestBody DecisionCommand c) { return ApiResponse.success(treatments.reviewChange(id, changeId, c, true)); }
    @PreAuthorize("hasRole('ADMIN')") @Operation(summary = "Quản trị từ chối đề xuất điều chỉnh")
    @PostMapping("/{id}/changes/{changeId}/reject") public ApiResponse<CourseView> reject(@PathVariable Long id, @PathVariable Long changeId, @RequestBody DecisionCommand c) { return ApiResponse.success(treatments.reviewChange(id, changeId, c, false)); }
    @Operation(summary = "Tạo yêu cầu vận hành cho hồ sơ điều trị")
    @PostMapping("/{id}/requests") public ApiResponse<CourseView> request(@PathVariable Long id, @RequestBody RequestCommand c) { return ApiResponse.success(treatments.request(id, c)); }
    @PreAuthorize("hasRole('ADMIN')") @Operation(summary = "Duyệt tạm dừng, tiếp tục, dừng, sự cố hoặc gia hạn")
    @PostMapping("/{id}/requests/{requestId}/approve") public ApiResponse<CourseView> approveRequest(@PathVariable Long id, @PathVariable Long requestId, @RequestBody DecisionCommand c) { return ApiResponse.success(treatments.reviewRequest(id, requestId, c, true)); }
    @PreAuthorize("hasRole('ADMIN')") @Operation(summary = "Từ chối yêu cầu thay đổi trạng thái hồ sơ")
    @PostMapping("/{id}/requests/{requestId}/reject") public ApiResponse<CourseView> rejectRequest(@PathVariable Long id, @PathVariable Long requestId, @RequestBody DecisionCommand c) { return ApiResponse.success(treatments.reviewRequest(id, requestId, c, false)); }
    @PreAuthorize("hasRole('ADMIN')") @Operation(summary = "Xác nhận bằng chứng đã hoàn đúng số tiền được duyệt")
    @PostMapping("/{id}/requests/{requestId}/refund-confirmation") public ApiResponse<CourseView> refund(@PathVariable Long id, @PathVariable Long requestId, @RequestBody RefundCommand c) { return ApiResponse.success(treatments.confirmRefund(id, requestId, c)); }
    @Operation(summary = "Ghi kết quả và hướng dẫn sau buổi, lưu lịch sử bổ sung")
    @PostMapping("/{id}/sessions/{sessionId}/result") public ApiResponse<CourseView> result(@PathVariable Long id, @PathVariable Long sessionId, @RequestBody ResultCommand c) { return ApiResponse.success(treatments.sessionResult(id, sessionId, c)); }
    @Operation(summary = "Tổng kết và kết thúc khi đủ buổi cùng kết quả bắt buộc")
    @PostMapping("/{id}/complete") public ApiResponse<CourseView> complete(@PathVariable Long id, @RequestBody CompleteCommand c) { return ApiResponse.success(treatments.complete(id, c)); }
}
