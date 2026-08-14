package com.core.beautyshop.dto.request;

import com.core.beautyshop.entities.product.enums.ProductStatus;
import com.core.beautyshop.entities.product.enums.ProductType;
import com.core.beautyshop.entities.product.enums.SkinType;
import com.core.beautyshop.entities.product.enums.TargetGender;
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
public class UpdateProductRequest {

    @Size(max = 255)
    private String name;

    @Size(max = 255)
    private String slug;

    @Size(max = 500)
    private String shortDescription;

    private String description;

    @Size(max = 500)
    private String thumbnailUrl;

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
}
