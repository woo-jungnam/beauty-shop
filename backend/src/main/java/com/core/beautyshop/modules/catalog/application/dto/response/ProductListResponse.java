package com.core.beautyshop.modules.catalog.application.dto.response;

import com.core.beautyshop.modules.catalog.domain.enums.ProductStatus;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
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

    // Constructor tường minh cho JPQL Constructor Expression
    // Thứ tự tham số phải khớp chính xác với thứ tự SELECT trong @Query
    public ProductListResponse(Long id, String name, String slug, String shortDescription,
                               String thumbnailUrl, BigDecimal basePrice, ProductStatus status,
                               Boolean isFeatured, Double averageRating, Integer totalReviews,
                               Long totalSold, String brandName) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.shortDescription = shortDescription;
        this.thumbnailUrl = thumbnailUrl;
        this.basePrice = basePrice;
        this.status = status;
        this.isFeatured = isFeatured;
        this.averageRating = averageRating;
        this.totalReviews = totalReviews;
        this.totalSold = totalSold;
        this.brandName = brandName;
    }
}
