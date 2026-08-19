package com.core.beautyshop.modules.catalog.application.dto.response;

import com.core.beautyshop.modules.catalog.domain.enums.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductListResponse {
    private Long id;
    private String name;
    private String slug;
    private String shortDescription;
    private String thumbnailUrl;
    private BigDecimal basePrice;
    private ProductStatus status;
    private Boolean isFeatured;
    private Double averageRating;
    private Integer totalReviews;
    private Long totalSold;
    private String brandName;
}
