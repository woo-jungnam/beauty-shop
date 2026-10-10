package com.core.beautyshop.modules.catalog.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
@Schema(description = "Dữ liệu ProductImageResponse")
public class ProductImageResponse {
    @Schema(description = "ID bản ghi")
    private Long id;
    @Schema(description = "ID sản phẩm")
    private Long productId;
    @Schema(description = "Đường dẫn ảnh")
    private String imageUrl;
    @Schema(description = "Văn bản thay thế")
    private String altText;
    @Schema(description = "Ảnh chính")
    private Boolean isPrimary;
    @Schema(description = "Thứ tự hiển thị")
    private Integer displayOrder;
    @Schema(description = "Thời điểm tạo UTC", format = "date-time")
    private Instant createdAt;
    @Schema(description = "Thời điểm cập nhật UTC", format = "date-time")
    private Instant updatedAt;
}
