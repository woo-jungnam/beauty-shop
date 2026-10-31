package com.core.beautyshop.modules.identity.api;
import com.core.beautyshop.modules.identity.application.service.CustomerAccountService;
import com.core.beautyshop.modules.identity.application.service.CustomerAccountService.*;
import com.core.beautyshop.shared.config.CorsConfig;
import com.core.beautyshop.shared.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
public class AccountRecoveryController {
    private final CustomerAccountService accounts;
    private final CorsConfig corsConfig;

    @PostMapping("/api/v1/auth/forgot-password")
    public ApiResponse<Void> forgot(@Valid @RequestBody EmailCommand c, HttpServletRequest request) {
        String origin = resolveAllowedOrigin(request);
        accounts.forgotPassword(c.email(), origin);
        return ApiResponse.success(null,"Nếu email hợp lệ đã đăng ký, hướng dẫn đặt lại mật khẩu sẽ được gửi. Vui lòng kiểm tra cả thư rác");
    }

    private String resolveAllowedOrigin(HttpServletRequest request) {
        if (request == null) return null;
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) {
            String referer = request.getHeader("Referer");
            if (referer != null && !referer.isBlank()) {
                try {
                    java.net.URI uri = java.net.URI.create(referer);
                    origin = uri.getScheme() + "://" + uri.getAuthority();
                } catch (Exception ignored) {}
            }
        }
        if (origin != null && !origin.isBlank()) {
            String trimmed = origin.trim().replaceAll("/+$", "");
            for (String allowed : corsConfig.getAllowedOrigins()) {
                if (trimmed.equalsIgnoreCase(allowed.trim().replaceAll("/+$", ""))) {
                    return trimmed;
                }
            }
        }
        return null;
    }

    @PostMapping("/api/v1/auth/reset-password") public ApiResponse<Void> reset(@Valid @RequestBody PasswordReset c){accounts.resetPassword(c);return ApiResponse.success(null,"Đã đặt lại mật khẩu. Vui lòng đăng nhập lại");}
    @PostMapping("/api/v1/newsletter/subscriptions") public ApiResponse<Void> newsletter(@Valid @RequestBody NewsletterCommand c){accounts.subscribe(c.email(),c.consent());return ApiResponse.success(null,"Đã đăng ký nhận bản tin");}
}
