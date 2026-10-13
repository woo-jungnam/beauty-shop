package com.core.beautyshop.modules.identity.api;

import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.modules.identity.application.dto.request.CreateRoleRequest;
import com.core.beautyshop.modules.identity.application.dto.request.UpdateRoleRequest;
import com.core.beautyshop.modules.identity.application.dto.response.RoleResponse;
import com.core.beautyshop.modules.identity.application.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Quản lý vai trò", description = "API quản lý vai trò")
@RestController
@RequestMapping("/api/v1/roles")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @Operation(summary = "Lấy danh sách tất cả vai trò (Admin)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getAllRoles() {
        return ResponseEntity.ok(ApiResponse.success(roleService.getAllRoles()));
    }

    @Operation(summary = "Lấy thông tin vai trò theo ID (Admin)")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleResponse>> getRoleById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(roleService.getRoleById(id)));
    }

    @Operation(summary = "Tạo vai trò mới (ADMIN)", description = "roleName là mã duy nhất, không tự chuẩn hóa hoặc tự cấp quyền endpoint. Những tuyến đã có hasRole dùng đúng mã ROLE_... hiện hành.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Vai trò được tạo", useReturnTypeSchema = true)
    @PostMapping
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(
            @Valid @RequestBody CreateRoleRequest request) {
        RoleResponse response = roleService.createRole(request);
        return ResponseEntity.status(201).body(ApiResponse.created(response, "Tạo vai trò thành công"));
    }

    @Operation(summary = "Cập nhật vai trò (ADMIN)", description = "Role hệ thống không được đổi mã; mô tả vẫn cập nhật được. Đổi mã role tùy chỉnh làm thu hồi phiên của các tài khoản đang có role đó.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleResponse>> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRoleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(roleService.updateRole(id, request)));
    }

    @Operation(summary = "Xóa vai trò tùy chỉnh (ADMIN)", description = "Không xóa role hệ thống. Role tùy chỉnh được gỡ khỏi các tài khoản liên quan, thu hồi phiên rồi xóa role.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
