package com.core.beautyshop.modules.identity.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Dữ liệu cập nhật role; mã hệ thống không được đổi")
public class UpdateRoleRequest {
    @NotBlank(message = "Tên quyền không được để trống")
    @Schema(description = "Mã role không rỗng, không tự thêm ROLE_ hoặc đổi hoa thường", requiredMode = Schema.RequiredMode.REQUIRED, example = "ROLE_CUSTOM_SUPPORT")
    private String roleName;

    @Schema(description = "Mô tả role")
    private String description;
}
