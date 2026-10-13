package com.core.beautyshop.modules.catalog.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Tạo danh mục")
public class CreateCategoryRequest {

    @NotBlank(message = "Tên danh mục không được để trống")
    @Size(max = 150)
    @Schema(description = "Tên danh mục", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 150)
    private String name;

    @NotBlank(message = "Slug không được để trống")
    @Size(max = 200)
    @Schema(description = "Slug duy nhất", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 200)
    private String slug;

    @Size(max = 500)
    @Schema(description = "Mô tả", maxLength = 500)
    private String description;

    @Size(max = 500)
    @Schema(description = "Đường dẫn ảnh", maxLength = 500)
    private String imageUrl;

    @Schema(description = "ID cha; tạo mới null là danh mục gốc", nullable = true)
    private Long parentId;

    @Schema(description = "Thứ tự hiển thị", example = "0")
    private Integer displayOrder;
}
