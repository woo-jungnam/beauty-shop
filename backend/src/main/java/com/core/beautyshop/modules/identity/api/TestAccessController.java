package com.core.beautyshop.modules.identity.api;

import io.swagger.v3.oas.annotations.tags.Tag;


import com.core.beautyshop.shared.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Quản lý quyền truy cập thử nghiệm", description = "API quản lý quyền truy cập thử nghiệm")
@RestController
@RequestMapping("/api/v1/test")
public class TestAccessController {

    @GetMapping("/public")
    public ResponseEntity<ApiResponse<Map<String, String>>> publicAccess() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Public content accessible to everyone")));
    }

    @GetMapping("/user")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> userAccess() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "User content accessible to USER and ADMIN roles")));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> adminAccess() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Admin content accessible ONLY to ADMIN role")));
    }
}
