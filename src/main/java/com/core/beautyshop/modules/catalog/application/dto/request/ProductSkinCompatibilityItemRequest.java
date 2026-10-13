package com.core.beautyshop.modules.catalog.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin độ tương thích với một loại da")
public class ProductSkinCompatibilityItemRequest {

    @NotNull(message = "ID loại da không được để trống")
    @Schema(description = "ID loại da từ danh mục skin_types", example = "1")
    private Long skinTypeId;

    @Schema(description = "Có khuyên dùng cho loại da này không", example = "true")
    @Builder.Default
    private Boolean isRecommended = true;

    @DecimalMin(value = "0.0", message = "Điểm số tối thiểu là 0.0")
    @DecimalMax(value = "1.0", message = "Điểm số tối đa là 1.0")
    @Schema(description = "Điểm số tương thích từ 0.00 đến 1.00", example = "0.95")
    private BigDecimal score;

    @Schema(description = "Lý do cảnh báo / chống chỉ định nếu không khuyên dùng", example = "Chứa cồn và BHA nồng độ cao dễ gây châm chích")
    private String contraindicationReason;
}
