package com.core.beautyshop.modules.identity.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
@Schema(description = "Thông tin role")
public class RoleResponse {
    @Schema(description = "ID role")
    private Long id;
    @Schema(description = "Mã role", example = "ROLE_STAFF")
    private String roleName;
    @Schema(description = "Mô tả")
    private String description;
    @Schema(description = "Thời điểm tạo UTC", format = "date-time")
    private Instant createdAt;
    @Schema(description = "Thời điểm cập nhật UTC", format = "date-time")
    private Instant updatedAt;
}
