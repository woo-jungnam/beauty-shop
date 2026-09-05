package com.core.beautyshop.modules.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.core.beautyshop.shared.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Kiểm tra quyền truy cập", description = "API thử nghiệm phân quyền hệ thống")
@RestController
@RequestMapping("/api/v1/test")
public class TestAccessController {

    @Operation(summary = "Kiểm tra truy cập công khai (Public)")
    @GetMapping("/public")
    public ResponseEntity<ApiResponse<Map<String, String>>> publicAccess() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Nội dung công khai có thể truy cập bởi mọi người")));
    }

    @Operation(summary = "Kiểm tra truy cập yêu cầu đăng nhập (User / Admin)")
    @GetMapping("/user")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> userAccess() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Nội dung người dùng có thể truy cập bởi vai trò USER và ADMIN")));
    }

    @Operation(summary = "Kiểm tra truy cập quyền quản trị (Chỉ Admin)")
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> adminAccess() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Nội dung quản trị viên CHỈ có thể truy cập bởi vai trò ADMIN")));
    }
}
