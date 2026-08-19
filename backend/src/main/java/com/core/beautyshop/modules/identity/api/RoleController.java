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

    @Operation(summary = "Tạo vai trò mới (Admin)")
    @PostMapping
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(
            @Valid @RequestBody CreateRoleRequest request) {
        RoleResponse response = roleService.createRole(request);
        return ResponseEntity.status(201).body(ApiResponse.created(response, "Tạo vai trò thành công"));
    }

    @Operation(summary = "Cập nhật vai trò (Admin)")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleResponse>> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRoleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(roleService.updateRole(id, request)));
    }

    @Operation(summary = "Xóa vai trò (Admin)")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
