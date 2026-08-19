package com.core.beautyshop.modules.catalog.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariantSummaryDto {
    private Long id;
    private Long productId;
    private String productName;
    private String sku;
    private String variantName;
    private BigDecimal price;
    private BigDecimal discountPrice;
    private Boolean isActive;
}
