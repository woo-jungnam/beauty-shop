package com.core.beautyshop.shared.audit.api;

import com.core.beautyshop.shared.audit.application.service.AuditLogService;
import com.core.beautyshop.shared.audit.domain.AuditLog;
import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.shared.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Nhật ký kiểm toán hệ thống (Admin)", description = "API truy vết hành động thay đổi dữ liệu, đăng nhập và nhật ký bảo mật của Quản trị viên")
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAuditController {
    private final AuditLogService auditLogService;

    @Operation(summary = "Tra cứu nhật ký kiểm toán", description = "ADMIN. keyword tìm trong username/resourceType/resourceId; action và status lọc chính xác. page bắt đầu từ 0, size mặc định 20; sort theo tên trường của bản ghi.")
    @GetMapping
    public ApiResponse<PageResponse<AuditLog>> list(@RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String action,
                                                     @RequestParam(required = false) String status,
                                                     @org.springdoc.core.annotations.ParameterObject Pageable pageable) {
        return ApiResponse.success(PageResponse.of(auditLogService.list(keyword, action, status, pageable)));
    }

    @Operation(summary = "Xem chi tiết một bản ghi kiểm toán theo ID")
    @GetMapping("/{id}")
    public ApiResponse<AuditLog> getById(@PathVariable Long id) {
        return ApiResponse.success(auditLogService.getById(id));
    }
}
