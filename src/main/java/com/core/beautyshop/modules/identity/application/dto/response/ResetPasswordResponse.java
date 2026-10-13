package com.core.beautyshop.modules.identity.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Kết quả đặt lại mật khẩu tạm thời cho người dùng")
public class ResetPasswordResponse {
    @Schema(description = "ID người dùng")
    private Long userId;

    @Schema(description = "Tên đăng nhập")
    private String username;

    @Schema(description = "Email tài khoản")
    private String email;

    @Schema(description = "Mật khẩu tạm thời mới vừa được tạo")
    private String temporaryPassword;

    @Schema(description = "Thông báo kết quả")
    private String message;
}
