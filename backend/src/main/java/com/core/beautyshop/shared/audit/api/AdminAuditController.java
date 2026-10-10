package com.core.beautyshop.shared.audit.api;

import com.core.beautyshop.shared.audit.domain.AuditLog;
import com.core.beautyshop.shared.audit.domain.AuditLogRepository;
import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.shared.dto.PageResponse;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Nhật ký kiểm toán hệ thống (Admin)", description = "API truy vết hành động thay đổi dữ liệu, đăng nhập và nhật ký bảo mật của Quản trị viên")
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAuditController {
    private final AuditLogRepository repository;

    @Operation(summary = "Tra cứu nhật ký kiểm toán", description = "ADMIN. keyword tìm trong username/resourceType/resourceId; action và status lọc chính xác. page bắt đầu từ 0, size mặc định 20; sort theo tên trường của bản ghi.")
    @GetMapping
    public ApiResponse<PageResponse<AuditLog>> list(@RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String action,
                                                     @RequestParam(required = false) String status,
                                                     @org.springdoc.core.annotations.ParameterObject Pageable pageable) {
        Specification<AuditLog> spec = Specification.where(null);
        if (keyword != null && !keyword.isBlank()) {
            String value = "%" + keyword.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("username")), value),
                    cb.like(cb.lower(root.get("resourceType")), value),
                    cb.like(cb.lower(root.get("resourceId")), value)));
        }
        if (action != null && !action.isBlank()) spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), action));
        if (status != null && !status.isBlank()) spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        return ApiResponse.success(PageResponse.of(repository.findAll(spec, pageable)));
    }

    @Operation(summary = "Xem chi tiết một bản ghi kiểm toán theo ID")
    @GetMapping("/{id}")
    public ApiResponse<AuditLog> getById(@PathVariable Long id) {
        return ApiResponse.success(repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Audit log not found: " + id)));
    }
}
