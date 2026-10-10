package com.core.beautyshop.modules.catalog.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProductAttributeRequest {
    @NotNull
    private Long attributeDefinitionId;
    private Long productVariantId;
    @NotBlank
    private String value;
}
