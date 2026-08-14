package com.core.beautyshop.dto.request;

import com.core.beautyshop.entities.product.enums.AttributeDataType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AttributeDefinitionRequest {
    @NotBlank(message = "Tên thuộc tính không được để trống")
    private String name;
    
    private String description;
    
    @NotNull(message = "Kiểu dữ liệu không được để trống")
    private AttributeDataType dataType;
}