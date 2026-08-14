package com.core.beautyshop.dto.request;

import com.core.beautyshop.entities.product.enums.ProductStatus;
import com.core.beautyshop.entities.product.enums.ProductType;
import com.core.beautyshop.entities.product.enums.SkinType;
import com.core.beautyshop.entities.product.enums.TargetGender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductRequest {

    @NotBlank(message = "Tên sản phẩm không được để trống")
    @Size(max = 255)
    private String name;

    @NotBlank(message = "Slug không được để trống")
    @Size(max = 255)
    private String slug;

    @Size(max = 500)
    private String shortDescription;

    private String description;

    @Size(max = 500)
    private String thumbnailUrl;

    @NotNull(message = "Giá gốc không được để trống")
    private BigDecimal basePrice;

    private ProductStatus status;
    private ProductType productType;
    private TargetGender targetGender;
    private SkinType skinType;

    private String ingredients;
    private String howToUse;

    @Size(max = 100)
    private String originCountry;

    @Size(max = 50)
    private String volume;

    private Boolean isFeatured;

    private Long brandId;
    private List<Long> categoryIds;
    private List<Long> tagIds;

    private List<VariantRequest> variants;
    private List<ImageRequest> images;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariantRequest {
        @NotBlank(message = "Mã SKU không được để trống")
        private String sku;

        @NotBlank(message = "Tên biến thể không được để trống")
        private String variantName;

        @NotNull(message = "Giá không được để trống")
        private BigDecimal price;

        private BigDecimal discountPrice;
        private String volume;
        private String color;
        private String barcode;
        private Boolean isDefault;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageRequest {
        @NotBlank(message = "Đường dẫn hình ảnh không được để trống")
        private String imageUrl;

        private String altText;
        private Integer displayOrder;
        private Boolean isPrimary;
    }
}
