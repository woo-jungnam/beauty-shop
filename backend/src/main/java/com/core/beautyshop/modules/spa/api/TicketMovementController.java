package com.core.beautyshop.modules.spa.api;

import com.core.beautyshop.modules.spa.application.service.SpaTicketMovementService;
import com.core.beautyshop.modules.spa.domain.TicketSessionMovement;
import com.core.beautyshop.shared.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Pageable;
import org.springdoc.core.annotations.ParameterObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@RestController @RequestMapping("/api/v1/spa/tickets") @RequiredArgsConstructor
@Tag(name = "Vé liệu trình Spa", description = "Tra cứu quyền lợi và lịch sử giữ/sử dụng/điều chỉnh lượt")
public class TicketMovementController {
    private final SpaTicketMovementService movementService;

    public record RefundHistory(java.math.BigDecimal amount, String reference, String reason, java.time.Instant createdAt) {}

    @GetMapping("/{id}/refunds") @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<RefundHistory>> refunds(@PathVariable Long id) {
        return ApiResponse.success(movementService.getRefunds(id));
    }

    @Operation(summary = "Phân trang sổ lượt và điều chỉnh của vé", description = "Chủ vé hoặc ADMIN/STAFF/SPA_RECEPTION, không cấp quyền cho therapist chỉ vì được giao item. Vé phải chưa xóa. Sắp xếp ID giảm dần, page=0/size mặc định 20; phân biệt RESERVE/REDEEM/RELEASE/FORFEIT/EXTEND/COMPENSATE/REFUND_CLOSE/LEGACY_ADJUSTMENT. Actor/item/service có thể null cho thao tác hệ thống, số dư lịch sử hoặc gia hạn; FORFEIT không chứng minh dịch vụ đã thực hiện hoặc đã thu phí tiền.")
    @GetMapping("/{id}/movements") @PreAuthorize("isAuthenticated()")
    public ApiResponse<PageResponse<TicketSessionMovement>> history(@PathVariable Long id,
            @ParameterObject @org.springframework.data.web.PageableDefault(size=20) Pageable pageable) {
        return ApiResponse.success(movementService.getMovements(id, pageable));
    }
}
