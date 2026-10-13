package com.core.beautyshop.modules.catalog.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Gắn ảnh bằng URL, không phải upload nhị phân")
public class ProductImageRequest {
    @NotBlank(message = "Đường dẫn hình ảnh không được để trống")
    @Schema(description = "Đường dẫn ảnh không rỗng; có thể dùng url từ API upload", requiredMode = Schema.RequiredMode.REQUIRED, example = "/uploads/image.png")
    private String imageUrl;

    @Schema(description = "Văn bản thay thế ảnh")
    private String altText;
    
    @Schema(description = "Chọn ảnh chính", example = "true")
    private Boolean isPrimary;
    
    @Schema(description = "Thứ tự hiển thị", example = "0")
    private Integer displayOrder;
}
