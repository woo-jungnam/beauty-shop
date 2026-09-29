package com.core.beautyshop.modules.identity.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin đăng nhập tài khoản")
public class LoginRequest {

    @NotBlank(message = "Tên đăng nhập hoặc Email không được để trống")
    @Schema(description = "Tên đăng nhập (username) hoặc địa chỉ Email đã đăng ký", example = "customer@beautyshop.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String usernameOrEmail;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Schema(description = "Mật khẩu tài khoản", example = "Password@123", requiredMode = Schema.RequiredMode.REQUIRED)
    @ToString.Exclude
    private String password;
}
