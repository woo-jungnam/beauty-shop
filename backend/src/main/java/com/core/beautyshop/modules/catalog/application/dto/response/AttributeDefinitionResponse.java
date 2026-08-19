package com.core.beautyshop.modules.catalog.application.dto.response;

import com.core.beautyshop.modules.catalog.domain.enums.AttributeDataType;
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
