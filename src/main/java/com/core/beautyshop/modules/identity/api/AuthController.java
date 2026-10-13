package com.core.beautyshop.modules.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

@Tag(name = "Xác thực & Người dùng", description = "Các API đăng nhập, đăng ký, cấp mới token và truy xuất hồ sơ tài khoản")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @Operation(summary = "Đăng nhập tài khoản và nhận cặp JWT", description = "Công khai, không cần access token. usernameOrEmail nhận username hoặc email; giá trị chứa @ được tra theo email. Tài khoản phải đang ACTIVE. Mỗi lần đăng nhập tạo một họ phiên riêng.")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest loginRequest
    ) {
        AuthResponse authResponse = authService.login(loginRequest);
        return ResponseEntity.ok(ApiResponse.success(authResponse));
    }

    @Operation(summary = "Đăng ký tài khoản khách hàng mới", description = "Công khai. Username dài 3–50 ký tự, không chứa @ hoặc khoảng trắng; email và username phải duy nhất. Tạo ROLE_CUSTOMER (hoặc ROLE_USER tương thích nếu hệ thống chỉ có role cũ), trả luôn cặp token; không có bước kích hoạt email trong API này.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tài khoản và phiên đăng nhập đã được tạo", useReturnTypeSchema = true)
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest registerRequest
    ) {
        AuthResponse authResponse = authService.register(registerRequest);
        return ResponseEntity.status(201).body(ApiResponse.created(authResponse, "Đăng ký tài khoản thành công"));
    }

    @Operation(summary = "Luân chuyển refresh token và cấp cặp token mới", description = "Không cần access token; gửi refreshToken còn hiệu lực trong body. Token cũ bị thu hồi khi luân chuyển. Gửi lại token đã luân chuyển làm thu hồi cả họ phiên; client cần thay cặp token và tránh refresh đồng thời.")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest refreshTokenRequest
    ) {
        AuthResponse authResponse = authService.refreshToken(refreshTokenRequest);
        return ResponseEntity.ok(ApiResponse.success(authResponse));
    }

    @Operation(summary = "Đăng xuất một họ phiên", description = "Không cần access token; gửi refreshToken trong body. Thu hồi cả access/refresh của họ phiên liên quan. Gửi lại yêu cầu hoặc token không còn phiên hoạt động vẫn trả thành công; không đăng xuất các họ phiên khác.")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody RefreshTokenRequest refreshTokenRequest
    ) {
        authService.logout(refreshTokenRequest);
        return ResponseEntity.ok(ApiResponse.success(null, "Đăng xuất thành công"));
    }

    @Operation(summary = "Lấy thông tin hồ sơ người dùng đang đăng nhập", description = "Yêu cầu Header Authorization: Bearer <access_token>.")
    @GetMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getCurrentUser() {
        UserProfileResponse profile = userService.getCurrentUserProfile();
        return ResponseEntity.ok(ApiResponse.success(profile));
    }
}
