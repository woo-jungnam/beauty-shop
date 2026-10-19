package com.core.beautyshop.modules.identity.api;
import com.core.beautyshop.modules.identity.application.service.CustomerAccountService;
import com.core.beautyshop.modules.identity.application.service.CustomerAccountService.*;
import com.core.beautyshop.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
public class AccountRecoveryController {
    private final CustomerAccountService accounts;
    @PostMapping("/api/v1/auth/forgot-password") public ApiResponse<Void> forgot(@Valid @RequestBody EmailCommand c){accounts.forgotPassword(c.email());return ApiResponse.success(null,"Nếu email hợp lệ đã đăng ký, hướng dẫn đặt lại mật khẩu sẽ được gửi. Vui lòng kiểm tra cả thư rác");}
    @PostMapping("/api/v1/auth/reset-password") public ApiResponse<Void> reset(@Valid @RequestBody PasswordReset c){accounts.resetPassword(c);return ApiResponse.success(null,"Đã đặt lại mật khẩu. Vui lòng đăng nhập lại");}
    @PostMapping("/api/v1/newsletter/subscriptions") public ApiResponse<Void> newsletter(@Valid @RequestBody NewsletterCommand c){accounts.subscribe(c.email(),c.consent());return ApiResponse.success(null,"Đã đăng ký nhận bản tin");}
}
