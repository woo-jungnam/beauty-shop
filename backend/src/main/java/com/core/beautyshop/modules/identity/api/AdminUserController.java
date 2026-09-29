package com.core.beautyshop.modules.identity.api;

import com.core.beautyshop.modules.identity.application.dto.response.UserProfileResponse;
import com.core.beautyshop.modules.identity.application.service.*;
import com.core.beautyshop.modules.identity.domain.enums.AccountStatus;
import com.core.beautyshop.shared.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {
    private final UserService userService;
    private final AuthService authService;
    @GetMapping public ApiResponse<PageResponse<UserProfileResponse>> list(Pageable pageable) { return ApiResponse.success(PageResponse.of(userService.getAllUsers(pageable))); }
    @GetMapping("/{id}") public ApiResponse<UserProfileResponse> get(@PathVariable Long id) { return ApiResponse.success(userService.getUserById(id)); }
    @PutMapping("/{id}/status") public ApiResponse<UserProfileResponse> status(@PathVariable Long id, @RequestBody StatusRequest request) {
        UserProfileResponse result = userService.updateStatus(id, request.status(), request.reason());
        if (request.status() != AccountStatus.ACTIVE) authService.forceLogoutUser(id);
        return ApiResponse.success(result);
    }
    @PostMapping("/{id}/force-logout") public ApiResponse<Void> forceLogout(@PathVariable Long id) { authService.forceLogoutUser(id); return ApiResponse.success(null); }
    public record StatusRequest(AccountStatus status, String reason) { }
}
