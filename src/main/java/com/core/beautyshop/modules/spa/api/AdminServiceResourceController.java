package com.core.beautyshop.modules.spa.api;

import com.core.beautyshop.modules.spa.application.service.ServiceResourceRequirementService;
import com.core.beautyshop.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin/spa/services")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')") @Tag(name = "Yêu cầu tài nguyên dịch vụ Spa (Admin)")
public class AdminServiceResourceController {
    private final ServiceResourceRequirementService service;
    @Operation(summary = "Xem yêu cầu tài nguyên hiện tại của dịch vụ", description = "Chỉ ADMIN; mỗi dòng gồm type và units. [] nghĩa không áp constraint resource tự động cho booking mới. Existing booking dùng snapshot đã lưu, không đọc lại cấu hình này.")
    @GetMapping("/{id}/resource-requirements")
    public ApiResponse<List<ServiceResourceRequirementService.RequirementCommand>> get(@PathVariable Long id) { return ApiResponse.success(service.get(id)); }
    @Operation(summary = "Thay toàn bộ yêu cầu tài nguyên dịch vụ", description = "Chỉ ADMIN; body là array type/units, type chữ hoa không trùng và units dương. Có thể yêu cầu BED+MACHINE cùng lúc; [] bỏ yêu cầu cho booking mới. Không tự tạo facility, không sửa requirement snapshot của lịch đã đặt; thiếu capacity sẽ không đặt được.")
    @PutMapping("/{id}/resource-requirements")
    public ApiResponse<List<ServiceResourceRequirementService.RequirementCommand>> replace(@PathVariable Long id,
            @RequestBody List<ServiceResourceRequirementService.RequirementCommand> commands) { return ApiResponse.success(service.replace(id, commands)); }
}
