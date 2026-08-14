package com.core.beautyshop.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class ProductVariantResponse {
    private Long id;
    private Long productId;
    private String sku;
    private String variantName;
    private BigDecimal price;
    private BigDecimal discountPrice;
    private String volume;
    private String color;
    private String barcode;
    private Boolean isDefault;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
}
