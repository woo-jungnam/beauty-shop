package com.core.beautyshop.modules.spa.api;

import com.core.beautyshop.modules.spa.domain.*;
import com.core.beautyshop.modules.spa.application.service.SpaAccessService;
import com.core.beautyshop.shared.dto.*;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Pageable;
import org.springdoc.core.annotations.ParameterObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController @RequestMapping("/api/v1/spa/tickets") @RequiredArgsConstructor
@Tag(name = "Vé liệu trình Spa", description = "Tra cứu quyền lợi và lịch sử giữ/sử dụng/điều chỉnh lượt")
public class TicketMovementController {
    private final UserServiceTicketRepository tickets;
    private final TicketSessionMovementRepository movements;
    private final SpaAccessService access;
    @Operation(summary = "Phân trang sổ lượt và điều chỉnh của vé", description = "Chủ vé hoặc ADMIN/STAFF/SPA_RECEPTION, không cấp quyền cho therapist chỉ vì được giao item. Vé phải chưa xóa. Sắp xếp ID giảm dần, page=0/size mặc định 20; phân biệt RESERVE/REDEEM/RELEASE/FORFEIT/EXTEND/COMPENSATE/LEGACY_ADJUSTMENT. Actor/item/service có thể null cho thao tác hệ thống, số dư lịch sử hoặc gia hạn; FORFEIT không chứng minh dịch vụ đã thực hiện hoặc đã thu phí tiền.")
    @GetMapping("/{id}/movements") @PreAuthorize("isAuthenticated()") @Transactional(readOnly=true)
    public ApiResponse<PageResponse<TicketSessionMovement>> history(@PathVariable Long id,
            @ParameterObject @org.springframework.data.web.PageableDefault(size=20) Pageable pageable) {
        var ticket = tickets.findById(id).filter(t -> !Boolean.TRUE.equals(t.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        if (!ticket.getUserId().equals(SecurityUtils.getCurrentUserId()) && !access.canManageReception())
            throw new AccessDeniedException("You cannot view this ticket");
        return ApiResponse.success(PageResponse.of(movements.findByTicketIdOrderByIdDesc(id, pageable)));
    }
}
