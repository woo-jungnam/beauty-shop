package com.core.beautyshop.dto.response;

import com.core.beautyshop.entities.product.enums.AttributeDataType;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class AttributeDefinitionResponse {
    private Long id;
    private String name;
    private String description;
    private AttributeDataType dataType;
    private Instant createdAt;
    private Instant updatedAt;
}
