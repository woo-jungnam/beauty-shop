package com.core.beautyshop.service.product;

import com.core.beautyshop.dto.request.AttributeDefinitionRequest;
import com.core.beautyshop.dto.request.AttributeValueRequest;
import com.core.beautyshop.dto.response.AttributeDefinitionResponse;
import com.core.beautyshop.dto.response.AttributeValueResponse;

import java.util.List;

public interface ProductAttributeService {
    List<AttributeDefinitionResponse> getAllDefinitions();
    AttributeDefinitionResponse getDefinitionById(Long id);
    AttributeDefinitionResponse createDefinition(AttributeDefinitionRequest request);
    AttributeDefinitionResponse updateDefinition(Long id, AttributeDefinitionRequest request);
    void deleteDefinition(Long id);

    List<AttributeValueResponse> getValuesByDefinitionId(Long definitionId);
    AttributeValueResponse addValue(AttributeValueRequest request);
    void deleteValue(Long id);
}