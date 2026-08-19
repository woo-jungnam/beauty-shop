package com.core.beautyshop.modules.catalog.application.service;

import com.core.beautyshop.modules.catalog.application.dto.request.AttributeDefinitionRequest;
import com.core.beautyshop.modules.catalog.application.dto.request.AttributeValueRequest;
import com.core.beautyshop.modules.catalog.application.dto.response.AttributeDefinitionResponse;
import com.core.beautyshop.modules.catalog.application.dto.response.AttributeValueResponse;
import com.core.beautyshop.modules.catalog.domain.ProductAttributeDefinition;
import com.core.beautyshop.modules.catalog.domain.ProductAttributeValue;
import com.core.beautyshop.modules.catalog.domain.Product;
import com.core.beautyshop.modules.catalog.domain.ProductVariant;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.modules.catalog.domain.ProductAttributeDefinitionRepository;
import com.core.beautyshop.modules.catalog.domain.ProductAttributeValueRepository;
import com.core.beautyshop.modules.catalog.domain.ProductRepository;
import com.core.beautyshop.modules.catalog.domain.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductAttributeServiceImpl implements ProductAttributeService {

    private final ProductAttributeDefinitionRepository definitionRepository;
    private final ProductAttributeValueRepository valueRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;

    @Override
    public List<AttributeDefinitionResponse> getAllDefinitions() {
        return definitionRepository.findAll().stream()
                .map(this::mapToDefinitionResponse)
                .collect(Collectors.toList());
    }

    @Override
    public AttributeDefinitionResponse getDefinitionById(Long id) {
        ProductAttributeDefinition def = definitionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy định nghĩa thuộc tính"));
        return mapToDefinitionResponse(def);
    }

    @Override
    @Transactional
    public AttributeDefinitionResponse createDefinition(AttributeDefinitionRequest request) {
        ProductAttributeDefinition def = ProductAttributeDefinition.builder()
                .attributeName(request.getName())
                .attributeCode(request.getName().toLowerCase().replace(" ", "_"))
                .build();
        def = definitionRepository.save(def);
        return mapToDefinitionResponse(def);
    }

    @Override
    @Transactional
    public AttributeDefinitionResponse updateDefinition(Long id, AttributeDefinitionRequest request) {
        ProductAttributeDefinition def = definitionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy định nghĩa thuộc tính"));
        
        def.setAttributeName(request.getName());
        def = definitionRepository.save(def);
        return mapToDefinitionResponse(def);
    }

    @Override
    @Transactional
    public void deleteDefinition(Long id) {
        if (!definitionRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy định nghĩa thuộc tính");
        }
        definitionRepository.deleteById(id);
    }

    @Override
    public List<AttributeValueResponse> getValuesByDefinitionId(Long definitionId) {
        return valueRepository.findByAttributeDefinitionId(definitionId).stream()
                .map(this::mapToValueResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AttributeValueResponse addValue(Long definitionId, AttributeValueRequest request) {
        request.setAttributeDefinitionId(definitionId);
        return addValue(request);
    }

    @Override
    @Transactional
    public AttributeValueResponse addValue(AttributeValueRequest request) {
        ProductAttributeDefinition def = definitionRepository.findById(request.getAttributeDefinitionId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy định nghĩa thuộc tính"));

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm"));

        ProductVariant variant = null;
        if (request.getProductVariantId() != null) {
            variant = productVariantRepository.findById(request.getProductVariantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy biến thể sản phẩm"));
        }

        ProductAttributeValue val = ProductAttributeValue.builder()
                .attributeDefinition(def)
                .product(product)
                .productVariant(variant)
                .valueString(request.getValue())
                .build();
        val = valueRepository.save(val);
        return mapToValueResponse(val);
    }

    @Override
    @Transactional
    public void deleteValue(Long id) {
        if (!valueRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy giá trị thuộc tính");
        }
        valueRepository.deleteById(id);
    }

    private AttributeDefinitionResponse mapToDefinitionResponse(ProductAttributeDefinition def) {
        return AttributeDefinitionResponse.builder()
                .id(def.getId())
                .name(def.getAttributeName())
                .createdAt(def.getCreatedAt())
                .updatedAt(def.getUpdatedAt())
                .build();
    }

    private AttributeValueResponse mapToValueResponse(ProductAttributeValue val) {
        return AttributeValueResponse.builder()
                .id(val.getId())
                .attributeDefinitionId(val.getAttributeDefinition().getId())
                .attributeDefinitionName(val.getAttributeDefinition().getAttributeName())
                .value(val.getValueString())
                .createdAt(val.getCreatedAt())
                .updatedAt(val.getUpdatedAt())
                .build();
    }
}
