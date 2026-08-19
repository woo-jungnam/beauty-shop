package com.core.beautyshop.modules.catalog.application.service;

import com.core.beautyshop.modules.catalog.application.dto.request.AttributeDefinitionRequest;
import com.core.beautyshop.modules.catalog.application.dto.request.AttributeValueRequest;
import com.core.beautyshop.modules.catalog.application.dto.response.AttributeDefinitionResponse;
import com.core.beautyshop.modules.catalog.application.dto.response.AttributeValueResponse;

import java.util.List;

public interface ProductAttributeService {
    List<AttributeDefinitionResponse> getAllDefinitions();
    AttributeDefinitionResponse getDefinitionById(Long id);
    AttributeDefinitionResponse createDefinition(AttributeDefinitionRequest request);
    AttributeDefinitionResponse updateDefinition(Long id, AttributeDefinitionRequest request);
    void deleteDefinition(Long id);

    List<AttributeValueResponse> getValuesByDefinitionId(Long definitionId);
    AttributeValueResponse addValue(AttributeValueRequest request);
    AttributeValueResponse addValue(Long definitionId, AttributeValueRequest request);
    void deleteValue(Long id);
}
