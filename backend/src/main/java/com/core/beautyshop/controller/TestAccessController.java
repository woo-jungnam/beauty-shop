package com.core.beautyshop.controller;

import io.swagger.v3.oas.annotations.tags.Tag;


import com.core.beautyshop.dto.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
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
    public ResponseEntity<ApiResponse<Map<String, String>>> publicAccess(HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Public content accessible to everyone"), request.getRequestURI()));
    }

    @GetMapping("/user")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> userAccess(HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "User content accessible to USER and ADMIN roles"), request.getRequestURI()));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> adminAccess(HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Admin content accessible ONLY to ADMIN role"), request.getRequestURI()));
    }
}
