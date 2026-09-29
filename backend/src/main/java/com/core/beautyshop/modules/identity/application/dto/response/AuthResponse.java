package com.core.beautyshop.modules.identity.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Phản hồi thông tin xác thực chứa JWT Token và người dùng")
public class AuthResponse {

    @Schema(description = "JWT Access Token dùng để gửi kèm các request sau qua Authorization header", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    @ToString.Exclude
    private String accessToken;

    @Schema(description = "JWT Refresh Token dùng để làm mới token khi hết hạn", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    @ToString.Exclude
    private String refreshToken;

    @Builder.Default
    @Schema(description = "Loại tiền tố Token (luôn là Bearer)", example = "Bearer")
    private String tokenType = "Bearer";

    @Schema(description = "ID định danh người dùng", example = "1")
    private Long id;

    @Schema(description = "Tên đăng nhập", example = "nguyenan")
    private String username;

    @Schema(description = "Địa chỉ email", example = "nguyenan@gmail.com")
    private String email;

    @Schema(description = "Họ và tên đầy đủ", example = "Nguyễn Văn An")
    private String fullName;

    @Schema(description = "Danh sách quyền hạn / vai trò", example = "[\"ROLE_CUSTOMER\"]")
    private List<String> roles;
}
