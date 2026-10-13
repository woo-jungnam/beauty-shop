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
@Schema(description = "Thông tin giải quyết vấn đề da liễu")
public class ProductSkinConcernItemRequest {

    @NotNull(message = "ID vấn đề da không được để trống")
    @Schema(description = "ID vấn đề da từ danh mục skin_concerns", example = "1")
    private Long concernId;

    @DecimalMin(value = "0.0", message = "Điểm số tối thiểu là 0.0")
    @DecimalMax(value = "1.0", message = "Điểm số tối đa là 1.0")
    @Schema(description = "Điểm hiệu quả điều trị từ 0.00 đến 1.00", example = "0.90")
    private BigDecimal score;

    @Schema(description = "Ghi chú lâm sàng / cơ chế tác động", example = "Hiệu quả rõ rệt sau 4 tuần sử dụng đều đặn")
    private String notes;
}
