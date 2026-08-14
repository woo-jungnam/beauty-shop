package com.core.beautyshop.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProductImageRequest {
    @NotBlank(message = "Đường dẫn hình ảnh không được để trống")
    private String imageUrl;

    private String altText;
    
    private Boolean isPrimary;
    
    private Integer displayOrder;
}