package com.core.beautyshop.modules.identity.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Dữ liệu tạo role; mã hệ thống không được đổi")
public class CreateRoleRequest {
    @NotBlank(message = "Tên quyền không được để trống")
    @jakarta.validation.constraints.Size(max = 50, message = "Tên quyền không được vượt quá 50 ký tự")
    @Schema(description = "Mã role không rỗng, không tự thêm ROLE_ hoặc đổi hoa thường", requiredMode = Schema.RequiredMode.REQUIRED, example = "ROLE_CUSTOM_SUPPORT")
    private String roleName;

    @jakarta.validation.constraints.Size(max = 255, message = "Mô tả quyền không được vượt quá 255 ký tự")
    @Schema(description = "Mô tả role")
    private String description;
}
