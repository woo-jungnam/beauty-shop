package com.core.beautyshop.modules.catalog.application.dto.response;

import com.core.beautyshop.modules.catalog.domain.enums.ProductStatus;
import com.core.beautyshop.modules.catalog.domain.enums.ProductType;
import com.core.beautyshop.modules.catalog.domain.enums.SkinType;
import com.core.beautyshop.modules.catalog.domain.enums.TargetGender;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {
    private Long id;
    private String name;
    private String slug;
    private String shortDescription;
    private String description;
    private String thumbnailUrl;
    private BigDecimal basePrice;
    private ProductStatus status;
    private ProductType productType;
    private TargetGender targetGender;
    private SkinType skinType;
    private String ingredients;
    private String howToUse;
    private String originCountry;
    private String volume;
    private Boolean isFeatured;
    private Double averageRating;
    private Integer totalReviews;
    private Long totalSold;
    private Instant createdAt;
    private Instant updatedAt;

    private BrandResponse brand;
    private List<CategoryResponse> categories;
    private List<VariantResponse> variants;
    private List<ImageResponse> images;
    private List<TagResponse> tags;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariantResponse {
        private Long id;
        private String sku;
        private String variantName;
        private BigDecimal price;
        private BigDecimal discountPrice;
        private String volume;
        private String color;
        private String barcode;
        private Boolean isDefault;
        private Boolean isActive;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageResponse {
        private Long id;
        private String imageUrl;
        private String altText;
        private Integer displayOrder;
        private Boolean isPrimary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TagResponse {
        private Long id;
        private String name;
        private String slug;
    }
}
