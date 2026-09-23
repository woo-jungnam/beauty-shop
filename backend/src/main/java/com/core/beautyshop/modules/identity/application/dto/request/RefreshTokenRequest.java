package com.core.beautyshop.modules.identity.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu cấp mới Access Token bằng Refresh Token")
public class RefreshTokenRequest {

    @NotBlank(message = "Refresh token không được để trống")
    @Schema(description = "JWT Refresh Token hợp lệ đã nhận khi đăng nhập", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...", requiredMode = Schema.RequiredMode.REQUIRED)
    @lombok.ToString.Exclude
    private String refreshToken;
}
