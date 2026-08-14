package com.core.beautyshop.controller;

import io.swagger.v3.oas.annotations.tags.Tag;


import com.core.beautyshop.dto.common.ApiResponse;
import com.core.beautyshop.dto.request.CreateRoleRequest;
import com.core.beautyshop.dto.request.UpdateRoleRequest;
import com.core.beautyshop.dto.response.RoleResponse;
import com.core.beautyshop.service.role.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Quản lý vai trò", description = "API quản lý vai trò")
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getAllRoles() {
        return ResponseEntity.ok(ApiResponse.<List<RoleResponse>>builder()
                .status(HttpStatus.OK.value())
                .message("Roles retrieved successfully")
                .data(roleService.getAllRoles())
                .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleResponse>> getRoleById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.<RoleResponse>builder()
                .status(HttpStatus.OK.value())
                .message("Role retrieved successfully")
                .data(roleService.getRoleById(id))
                .build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(@Valid @RequestBody CreateRoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<RoleResponse>builder()
                        .status(HttpStatus.CREATED.value())
                        .message("Role created successfully")
                        .data(roleService.createRole(request))
                        .build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleResponse>> updateRole(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request) {
        return ResponseEntity.ok(ApiResponse.<RoleResponse>builder()
                .status(HttpStatus.OK.value())
                .message("Role updated successfully")
                .data(roleService.updateRole(id, request))
                .build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Role deleted successfully")
                .build());
    }
}