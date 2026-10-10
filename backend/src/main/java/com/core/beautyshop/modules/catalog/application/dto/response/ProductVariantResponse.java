package com.core.beautyshop.modules.catalog.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@Schema(description = "Dữ liệu ProductVariantResponse")
public class ProductVariantResponse {
    @Schema(description = "ID bản ghi")
    private Long id;
    @Schema(description = "ID sản phẩm")
    private Long productId;
    @Schema(description = "Mã SKU")
    private String sku;
    @Schema(description = "Tên SKU")
    private String variantName;
    @Schema(description = "Giá gốc VND")
    private BigDecimal price;
    @Schema(description = "Giá giảm VND, null nếu không giảm")
    private BigDecimal discountPrice;
    @Schema(description = "Dung tích")
    private String volume;
    @Schema(description = "Màu")
    private String color;
    @Schema(description = "Mã vạch")
    private String barcode;
    @Schema(description = "SKU mặc định")
    private Boolean isDefault;
    @Schema(description = "Trạng thái kích hoạt")
    private Boolean isActive;
    @Schema(description = "Thời điểm tạo UTC", format = "date-time")
    private Instant createdAt;
    @Schema(description = "Thời điểm cập nhật UTC", format = "date-time")
    private Instant updatedAt;
}
