package com.core.beautyshop.modules.identity.api;

import io.swagger.v3.oas.annotations.tags.Tag;


import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.modules.identity.application.dto.request.LoginRequest;
import com.core.beautyshop.modules.identity.application.dto.request.RefreshTokenRequest;
import com.core.beautyshop.modules.identity.application.dto.request.RegisterRequest;
import com.core.beautyshop.modules.identity.application.dto.response.AuthResponse;
import com.core.beautyshop.modules.identity.application.dto.response.UserProfileResponse;
import com.core.beautyshop.modules.identity.application.service.AuthService;
import com.core.beautyshop.modules.identity.application.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Quản lý đăng nhập và đăng ký", description = "API cho phần quản lý đăng nhập và đăng ký tài khoản")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest loginRequest
    ) {
        AuthResponse authResponse = authService.login(loginRequest);
        return ResponseEntity.ok(ApiResponse.success(authResponse));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest registerRequest
    ) {
        AuthResponse authResponse = authService.register(registerRequest);
        return ResponseEntity.status(201).body(ApiResponse.created(authResponse, "User registered successfully"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest refreshTokenRequest
    ) {
        AuthResponse authResponse = authService.refreshToken(refreshTokenRequest);
        return ResponseEntity.ok(ApiResponse.success(authResponse));
    }

    @GetMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getCurrentUser() {
        UserProfileResponse profile = userService.getCurrentUserProfile();
        return ResponseEntity.ok(ApiResponse.success(profile));
    }
}
