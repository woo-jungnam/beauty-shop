package com.core.beautyshop.modules.catalog.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Tạo/cập nhật tag, chỉ ADMIN")
public class TagRequest {
    @NotBlank(message = "Tên thẻ không được để trống")
    @Schema(description = "Tên tag không rỗng", requiredMode = Schema.RequiredMode.REQUIRED, example = "Chăm sóc da")
    private String name;
}
