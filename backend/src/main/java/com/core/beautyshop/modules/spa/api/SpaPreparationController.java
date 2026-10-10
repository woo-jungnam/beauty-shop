package com.core.beautyshop.modules.spa.api;

import com.core.beautyshop.modules.spa.application.service.SpaPreparationService;
import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.shared.audit.api.annotation.AuditAction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/spa/appointments/{id}")
@Tag(name = "Chuẩn bị và đồng ý quy trình Spa", description = "Yêu cầu đã chụp tại booking; bằng chứng của khách, cảnh báo cho người thực hiện và ngoại lệ ADMIN")
public class SpaPreparationController {
    private final SpaPreparationService preparation;

    @Operation(summary = "Xem hồ sơ chăm sóc và versionHash hiện tại", description = "ADMIN/STAFF/SPA_RECEPTION/CS_STAFF hoặc SPA_THERAPIST được giao lịch. Khách sở hữu cũng không được đọc private CRM notes qua endpoint này. Hash gồm care notes và các câu trả lời form hiện tại của lịch; dùng hash này để acknowledge trước START khi warningsRequired.")
    @GetMapping("/care-summary")
    public ApiResponse<SpaPreparationService.CareSummary> care(@PathVariable Long id) {
        return ApiResponse.success(preparation.careSummary(id));
    }

    @Operation(summary = "Xem yêu cầu chuẩn bị đã chụp theo từng item", description = "Chủ lịch hoặc ADMIN/STAFF/SPA_RECEPTION/SPA_THERAPIST được giao. Trả form-version, latest response và lịch sử acknowledgment/override; không trả private care notes. Legacy booking không có snapshot được đánh dấu legacyBooking và không tự áp yêu cầu mới.")
    @GetMapping("/preparation")
    public ApiResponse<List<SpaPreparationService.ItemPreparation>> forms(@PathVariable Long id) {
        return ApiResponse.success(preparation.preparation(id));
    }

    @Operation(summary = "Khách nộp và xác nhận form đã đặt", description = "Chỉ CUSTOMER/USER sở hữu lịch; nhân viên/ADMIN không được nộp thay khách khác. requirementId là ID yêu cầu trong preparation, không phải template/versionId. Lịch PENDING/CONFIRMED/IN_PROGRESS nhưng item còn PLANNED chưa START. acknowledged phải true, answers đúng form-version; mỗi lần nộp tạo bản ghi append-only, chưa hỗ trợ từ chối/rút consent.")
    @PostMapping("/preparation/forms/{requirementId}/responses")
    @ResponseStatus(HttpStatus.CREATED)
    @AuditAction(action="SUBMIT_CUSTOMER_CONSENT", resourceType="APPOINTMENT")
    public ApiResponse<SpaPreparationService.FormResponse> submit(@PathVariable Long id, @Parameter(description = "ID requirement của item trong GET preparation, không phải formVersionId") @PathVariable Long requirementId,
                                                                  @RequestBody SpaPreparationService.ResponseCommand command) {
        return ApiResponse.created(preparation.submit(id, requirementId, command), "Customer consent recorded");
    }

    @Operation(summary = "Người thực hiện xác nhận đã đọc cảnh báo", description = "ADMIN/STAFF hoặc SPA_THERAPIST được giao đúng item. Item PLANNED, chưa START; gửi versionHash hiện tại từ care-summary. Hồ sơ/form thay đổi làm hash cũ không hợp lệ. START kiểm acknowledgment của chính actor bắt đầu; không xác nhận thay người khác.")
    @PostMapping("/preparation/items/{itemId}/acknowledge-warnings")
    @ResponseStatus(HttpStatus.CREATED)
    @AuditAction(action="ACKNOWLEDGE_CARE_WARNINGS", resourceType="APPOINTMENT")
    public ApiResponse<SpaPreparationService.WarningAcknowledgment> acknowledge(@PathVariable Long id, @PathVariable Long itemId,
                                                                               @RequestBody SpaPreparationService.WarningCommand command) {
        return ApiResponse.created(preparation.acknowledgeWarnings(id, itemId, command), "Care warnings acknowledged");
    }

    @Operation(summary = "ADMIN duyệt ngoại lệ yêu cầu chuẩn bị", description = "Chỉ ADMIN, item PLANNED chưa START trong lịch đang mở. reason bắt buộc, tối đa 1000 ký tự. Lưu override riêng để bỏ qua toàn bộ yêu cầu form/cảnh báo của item; không tạo consent giả. Chưa có endpoint revoke override.")
    @PostMapping("/preparation/items/{itemId}/override")
    @ResponseStatus(HttpStatus.CREATED)
    @AuditAction(action="OVERRIDE_PREPARATION", resourceType="APPOINTMENT")
    public ApiResponse<SpaPreparationService.OverrideEvidence> override(@PathVariable Long id, @PathVariable Long itemId,
                                                                        @RequestBody SpaPreparationService.OverrideCommand command) {
        return ApiResponse.created(preparation.override(id, itemId, command), "Manager override recorded");
    }
}
