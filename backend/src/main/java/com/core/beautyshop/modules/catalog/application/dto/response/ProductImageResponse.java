package com.core.beautyshop.modules.catalog.application.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class ProductImageResponse {
    private Long id;
    private Long productId;
    private String imageUrl;
    private String altText;
    private Boolean isPrimary;
    private Integer displayOrder;
    private Instant createdAt;
    private Instant updatedAt;
}
