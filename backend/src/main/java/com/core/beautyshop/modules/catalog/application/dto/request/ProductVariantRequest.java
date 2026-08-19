package com.core.beautyshop.modules.catalog.application.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProductVariantRequest {
    @NotBlank(message = "Mã SKU không được để trống")
    private String sku;

    @NotBlank(message = "Tên biến thể không được để trống")
    private String variantName;

    @NotNull(message = "Giá không được để trống")
    @DecimalMin(value = "0.0", message = "Giá phải lớn hơn hoặc bằng 0")
    private BigDecimal price;

    @DecimalMin(value = "0.0", message = "Giá giảm phải lớn hơn hoặc bằng 0")
    private BigDecimal discountPrice;

    private String volume;
    private String color;
    private String barcode;
    private Boolean isDefault;
    private Boolean isActive;
}
