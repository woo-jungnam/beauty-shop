package com.core.beautyshop.modules.catalog.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AttributeValueRequest {
    @NotNull(message = "ID định nghĩa thuộc tính không được để trống")
    private Long attributeDefinitionId;
    
    @NotNull(message = "ID sản phẩm không được để trống")
    private Long productId;
    
    private Long productVariantId;
    
    @NotBlank(message = "Giá trị không được để trống")
    private String value;
}
