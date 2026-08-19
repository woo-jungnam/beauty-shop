package com.core.beautyshop.modules.catalog.application.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class AttributeValueResponse {
    private Long id;
    private Long attributeDefinitionId;
    private String attributeDefinitionName;
    private String value;
    private Instant createdAt;
    private Instant updatedAt;
}
