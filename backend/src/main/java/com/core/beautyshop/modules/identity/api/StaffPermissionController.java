package com.core.beautyshop.modules.identity.api;
import com.core.beautyshop.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Set;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin/users") @PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Phân quyền nhân viên (Admin)", description = "Quản lý danh mục quyền hạn và gán quyền chi tiết cho tài khoản nhân viên")
public class StaffPermissionController {
    private final StaffPermissionService permissions;

    @Operation(summary = "Danh mục mã quyền nhân viên hệ thống (Admin)", description = "Lấy danh sách tất cả các quyền hạn chi tiết có thể gán cho tài khoản nhân viên")
    @GetMapping("/staff-permissions") public ApiResponse<List<String>> known(){return ApiResponse.success(StaffPermissionService.KNOWN);}

    @Operation(summary = "Xem danh sách quyền của một nhân viên (Admin)", description = "Tra cứu các quyền hạn chi tiết đang được cấp cho tài khoản nhân viên cụ thể")
    @GetMapping("/{id}/permissions") public ApiResponse<List<String>> get(@PathVariable Long id){return ApiResponse.success(permissions.forUser(id));}

    @Operation(summary = "Cập nhật phân quyền cho nhân viên (Admin)", description = "Gán mới hoặc thay đổi danh sách quyền chi tiết cho tài khoản nhân viên")
    @PutMapping("/{id}/permissions") public ApiResponse<List<String>> update(@PathVariable Long id,@RequestBody Command c){return ApiResponse.success(permissions.replace(id,c.permissions()));}

    public record Command(Set<String> permissions){}
}
