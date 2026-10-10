package com.core.beautyshop.modules.spa.api;

import com.core.beautyshop.modules.spa.application.dto.response.UserServiceTicketResponse;
import com.core.beautyshop.modules.spa.application.service.AdminSpaTicketService;
import com.core.beautyshop.modules.spa.domain.enums.TicketStatus;
import com.core.beautyshop.shared.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springdoc.core.annotations.ParameterObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Thẻ liệu trình Spa khách hàng (Admin)", description = "API tra cứu vé dịch vụ của khách hàng, gia hạn ngày hết hạn và đền bù buổi liệu trình")
@RestController
@RequestMapping("/api/v1/admin/spa/tickets")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
@RequiredArgsConstructor
public class AdminSpaTicketController {
    private final AdminSpaTicketService service;

    @Operation(summary = "Tìm kiếm vé Spa theo khách/trạng thái", description = "Chỉ ADMIN/STAFF; phân trang các vé chưa xóa, userId/status tùy chọn. used có thể gồm forfeiture/legacy, không đồng nghĩa mọi lượt đã phục vụ.")
    @GetMapping
    public ApiResponse<PageResponse<UserServiceTicketResponse>> find(@Parameter(description = "ID tài khoản chủ vé") @RequestParam(required = false) Long userId, @Parameter(description = "Trạng thái lưu trên vé") @RequestParam(required = false) TicketStatus status, @ParameterObject Pageable pageable) {
        return ApiResponse.success(PageResponse.of(service.find(userId, status, pageable)));
    }

    @Operation(summary = "Xem chi tiết vé Spa của khách", description = "Chỉ ADMIN/STAFF; vé chưa xóa. Trả total/used/reserved và quota khả dụng theo từng dịch vụ.")
    @GetMapping("/{id}")
    public ApiResponse<UserServiceTicketResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(service.get(id));
    }

    @Operation(summary = "Gia hạn vé Spa với bằng chứng điều chỉnh", description = "ADMIN/STAFF; days 1–3650, reason và idempotencyKey trong JSON bắt buộc. Vé phải paid, không REVOKED; vé expiry null không cần gia hạn và bị từ chối. Nếu còn quyền thì dịch vụ phải khả dụng; gia hạn từ max(expiry,now). Cùng key/nội dung trả lại kết quả, cùng key khác điều chỉnh bị từ chối. Không thay tiền hoặc tự hoàn.")
    @PutMapping("/{id}/extend")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<UserServiceTicketResponse> extend(@PathVariable Long id, @jakarta.validation.Valid @RequestBody ExtendRequest request) {
        return ApiResponse.success(service.extend(id, request.days(), request.reason(), request.idempotencyKey()));
    }

    @Operation(summary = "Bù lượt cho một dịch vụ trong vé", description = "ADMIN/STAFF; serviceId phải có trong entitlement, sessions 1–100; reason và idempotencyKey trong JSON bắt buộc. Vé paid/chưa revoked, dịch vụ khả dụng. Tăng total của đúng entitlement và vé, ghi ledger; không tự kéo dài hạn vé hoặc thu tiền.")
    @PutMapping("/{id}/compensate")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<UserServiceTicketResponse> compensate(@PathVariable Long id, @jakarta.validation.Valid @RequestBody CompensationRequest request) {
        return ApiResponse.success(service.compensate(id, request.serviceId(), request.sessions(), request.reason(), request.idempotencyKey()));
    }

    @Schema(name = "SpaTicketExtensionRequest", description = "Gia hạn quản trị; khóa chống lặp nằm trong body, không phải header")
    public record ExtendRequest(@Schema(minimum = "1", maximum = "3650", requiredMode = Schema.RequiredMode.REQUIRED, example = "30") int days,
            @Schema(maxLength = 1000, requiredMode = Schema.RequiredMode.REQUIRED, example = "Gia hạn theo phê duyệt quản lý") @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=1000) String reason,
            @Schema(pattern = "[A-Za-z0-9:._-]{1,80}", requiredMode = Schema.RequiredMode.REQUIRED, example = "ticket-101:extend:001") @jakarta.validation.constraints.NotBlank String idempotencyKey) { }
    @Schema(name = "SpaTicketCompensationRequest", description = "Bù quota cho dịch vụ đã có trong vé; không thay expiry")
    public record CompensationRequest(@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1") Long serviceId,
            @Schema(minimum = "1", maximum = "100", requiredMode = Schema.RequiredMode.REQUIRED, example = "1") int sessions,
            @Schema(maxLength = 1000, requiredMode = Schema.RequiredMode.REQUIRED, example = "Bù lượt theo biên bản xử lý") @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=1000) String reason,
            @Schema(pattern = "[A-Za-z0-9:._-]{1,80}", requiredMode = Schema.RequiredMode.REQUIRED, example = "ticket-101:compensate:001") @jakarta.validation.constraints.NotBlank String idempotencyKey) { }
}
