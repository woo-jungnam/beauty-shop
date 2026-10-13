package com.core.beautyshop.modules.catalog.application.service;

import com.core.beautyshop.modules.catalog.application.dto.request.AttributeDefinitionRequest;
import com.core.beautyshop.modules.catalog.application.dto.request.AttributeValueRequest;
import com.core.beautyshop.modules.catalog.application.dto.request.ProductAttributeRequest;
import java.util.ArrayList;
import java.util.HashSet;
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
import com.core.beautyshop.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.core.beautyshop.modules.catalog.domain.enums.AttributeDataType;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductAttributeServiceImpl implements ProductAttributeService {

    private final ProductAttributeDefinitionRepository definitionRepository;
    private final ProductAttributeValueRepository valueRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AttributeDefinitionResponse> getAllDefinitions() {
        return definitionRepository.findAll().stream().filter(def -> !Boolean.TRUE.equals(def.getIsDeleted()))
                .map(this::mapToDefinitionResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public AttributeDefinitionResponse getDefinitionById(Long id) {
        ProductAttributeDefinition def = definitionRepository.findById(id).filter(value -> !Boolean.TRUE.equals(value.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy định nghĩa thuộc tính"));
        return mapToDefinitionResponse(def);
    }

    @Override
    @Transactional
    public AttributeDefinitionResponse createDefinition(AttributeDefinitionRequest request) {
        validateDefinition(request);
        String code = request.getName().trim().toLowerCase(Locale.ROOT).replace(" ", "_");
        if (definitionRepository.findByAttributeCode(code).isPresent()) throw new BusinessException("Mã thuộc tính đã tồn tại");
        ProductAttributeDefinition def = ProductAttributeDefinition.builder()
                .attributeName(request.getName().trim())
                .description(request.getDescription())
                .attributeCode(code)
                .dataType(request.getDataType())
                .build();
        def = definitionRepository.save(def);
        return mapToDefinitionResponse(def);
    }

    @Override
    @Transactional
    public AttributeDefinitionResponse updateDefinition(Long id, AttributeDefinitionRequest request) {
        validateDefinition(request);
        ProductAttributeDefinition def = definitionRepository.findById(id).filter(value -> !Boolean.TRUE.equals(value.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy định nghĩa thuộc tính"));
        
        if (def.getDataType() != request.getDataType() && valueRepository.existsByAttributeDefinitionIdAndIsDeletedFalse(id)) {
            throw new BusinessException("Không thể đổi kiểu thuộc tính đang có giá trị; cần chuyển đổi dữ liệu trước");
        }
        def.setAttributeName(request.getName().trim());
        def.setDataType(request.getDataType());
        if (request.getDescription() != null) def.setDescription(request.getDescription());
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
    @Transactional(readOnly = true)
    public List<AttributeValueResponse> getValuesByDefinitionId(Long definitionId) {
        return valueRepository.findByAttributeDefinitionId(definitionId).stream().filter(value -> !Boolean.TRUE.equals(value.getIsDeleted()))
                .map(ProductAttributeServiceImpl::mapToValueResponse)
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
        if (request == null || request.getAttributeDefinitionId() == null) {
            throw new BusinessException("ID định nghĩa thuộc tính không được để trống");
        }
        ProductAttributeDefinition def = definitionRepository.findById(request.getAttributeDefinitionId()).filter(value -> !Boolean.TRUE.equals(value.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy định nghĩa thuộc tính"));

        Product product = productRepository.findByIdAndIsDeletedFalse(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm"));

        ProductVariant variant = null;
        if (request.getProductVariantId() != null) {
            variant = productVariantRepository.findByIdAndIsDeletedFalse(request.getProductVariantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy biến thể sản phẩm"));
            if (!product.getId().equals(variant.getProduct().getId())) {
                throw new BusinessException(
                        "Thuộc tính không thuộc sản phẩm đã chọn");
            }
        }

        ProductAttributeValue val = ProductAttributeValue.builder()
                .attributeDefinition(def)
                .product(product)
                .productVariant(variant)
                .build();
        setTypedValue(val, def.getDataType(), request.getValue());
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

    @Override
    @Transactional
    public List<AttributeValueResponse> replaceProductValues(Long productId,
            List<ProductAttributeRequest> requests) {
        if (requests == null) throw new BusinessException("Danh sách thuộc tính không được null");
        Product product = productRepository.findByIdAndIsDeletedFalse(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm"));
        var keys = new HashSet<String>();
        var replacements = new ArrayList<ProductAttributeValue>();
        for (var request : requests) {
            if (request == null || request.getAttributeDefinitionId() == null)
                throw new BusinessException("Cần chọn định nghĩa thuộc tính");
            String key = request.getAttributeDefinitionId() + ":" + request.getProductVariantId();
            if (!keys.add(key)) throw new BusinessException("Thuộc tính trùng trong cùng sản phẩm hoặc SKU");
            var definition = definitionRepository.findById(request.getAttributeDefinitionId())
                    .filter(def -> !Boolean.TRUE.equals(def.getIsDeleted()))
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy định nghĩa thuộc tính"));
            ProductVariant variant = null;
            if (request.getProductVariantId() != null) {
                variant = productVariantRepository.findByIdAndIsDeletedFalse(request.getProductVariantId())
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy SKU"));
                if (!productId.equals(variant.getProduct().getId()))
                    throw new BusinessException("SKU không thuộc sản phẩm");
            }
            var value = ProductAttributeValue.builder().product(product).productVariant(variant)
                    .attributeDefinition(definition).build();
            setTypedValue(value, definition.getDataType(), request.getValue());
            replacements.add(value);
        }
        // Validate every row before replacing; rollback preserves the original list on failure.
        product.getAttributeValues().clear();
        productRepository.flush();
        var saved = valueRepository.saveAllAndFlush(replacements);
        product.getAttributeValues().addAll(saved);
        return saved.stream().map(ProductAttributeServiceImpl::mapToValueResponse).toList();
    }

    private AttributeDefinitionResponse mapToDefinitionResponse(ProductAttributeDefinition def) {
        return AttributeDefinitionResponse.builder()
                .id(def.getId())
                .name(def.getAttributeName())
                .description(def.getDescription())
                .dataType(def.getDataType())
                .createdAt(def.getCreatedAt())
                .updatedAt(def.getUpdatedAt())
                .build();
    }

    static AttributeValueResponse mapToValueResponse(ProductAttributeValue val) {
        return AttributeValueResponse.builder()
                .id(val.getId())
                .attributeDefinitionId(val.getAttributeDefinition().getId())
                .attributeDefinitionName(val.getAttributeDefinition().getAttributeName())
                .value(valueAsString(val))
                .dataType(val.getAttributeDefinition().getDataType())
                .productVariantId(val.getProductVariant() == null ? null : val.getProductVariant().getId())
                .createdAt(val.getCreatedAt())
                .updatedAt(val.getUpdatedAt())
                .build();
    }

    private void validateDefinition(AttributeDefinitionRequest request) {
        if (request == null || request.getName() == null || request.getName().isBlank() || request.getDataType() == null) {
            throw new BusinessException("Tên và kiểu dữ liệu thuộc tính không được để trống");
        }
    }

    private void setTypedValue(ProductAttributeValue value, AttributeDataType type, String input) {
        if (input == null || input.isBlank()) throw new BusinessException("Giá trị thuộc tính không được để trống");
        String text = input.trim();
        try {
            switch (type) {
                case NUMBER -> {
                    double number = new BigDecimal(text).doubleValue();
                    if (!Double.isFinite(number)) throw new IllegalArgumentException();
                    value.setValueNumber(number);
                }
                case BOOLEAN -> {
                    if (!text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false")) throw new IllegalArgumentException();
                    value.setValueBoolean(Boolean.parseBoolean(text));
                }
                case DATE -> value.setValueString(LocalDate.parse(text).toString());
                case TEXT_AREA -> value.setValueText(text);
                case STRING -> {
                    if (text.length() > 500) throw new IllegalArgumentException();
                    value.setValueString(text);
                }
            }
        } catch (IllegalArgumentException | java.time.format.DateTimeParseException exception) {
            throw new BusinessException("Giá trị không hợp lệ cho kiểu thuộc tính " + type);
        }
    }

    private static String valueAsString(ProductAttributeValue value) {
        if (value.getValueNumber() != null) return BigDecimal.valueOf(value.getValueNumber()).stripTrailingZeros().toPlainString();
        if (value.getValueBoolean() != null) return value.getValueBoolean().toString();
        if (value.getValueText() != null) return value.getValueText();
        return value.getValueString();
    }
}
