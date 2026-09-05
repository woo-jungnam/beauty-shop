package com.core.beautyshop.modules.catalog.application.service;

import com.core.beautyshop.modules.catalog.application.dto.request.ProductVariantRequest;
import com.core.beautyshop.modules.catalog.application.dto.response.ProductVariantResponse;
import com.core.beautyshop.modules.catalog.domain.Product;
import com.core.beautyshop.modules.catalog.domain.ProductVariant;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.modules.catalog.domain.ProductRepository;
import com.core.beautyshop.modules.catalog.domain.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductVariantServiceImpl implements ProductVariantService {

    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ProductVariantResponse> getVariantsByProductId(Long productId) {
        return variantRepository.findVariantResponsesByProductId(productId);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductVariantResponse getVariantById(Long variantId) {
        return variantRepository.findVariantResponseById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy biến thể với id: " + variantId));
    }

    @Override
    @Transactional
    public ProductVariantResponse addVariant(Long productId, ProductVariantRequest request) {
        Product product = productRepository.findByIdAndIsDeletedFalse(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với id: " + productId));

        if (variantRepository.existsBySku(request.getSku())) {
            throw new BusinessException("Mã SKU đã tồn tại: " + request.getSku());
        }

        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .sku(request.getSku())
                .variantName(request.getVariantName())
                .price(request.getPrice())
                .discountPrice(request.getDiscountPrice())
                .volume(request.getVolume())
                .color(request.getColor())
                .barcode(request.getBarcode())
                .isDefault(request.getIsDefault() != null ? request.getIsDefault() : false)
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        variant = variantRepository.save(variant);
        return mapToResponse(variant);
    }

    @Override
    @Transactional
    public ProductVariantResponse updateVariant(Long variantId, ProductVariantRequest request) {
        ProductVariant variant = variantRepository.findByIdAndIsDeletedFalse(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy biến thể với id: " + variantId));

        if (!variant.getSku().equals(request.getSku()) && variantRepository.existsBySku(request.getSku())) {
            throw new BusinessException("Mã SKU đã tồn tại: " + request.getSku());
        }

        variant.setSku(request.getSku());
        variant.setVariantName(request.getVariantName());
        variant.setPrice(request.getPrice());
        variant.setDiscountPrice(request.getDiscountPrice());
        variant.setVolume(request.getVolume());
        variant.setColor(request.getColor());
        variant.setBarcode(request.getBarcode());
        
        if (request.getIsDefault() != null) variant.setIsDefault(request.getIsDefault());
        if (request.getIsActive() != null) variant.setIsActive(request.getIsActive());

        variant = variantRepository.save(variant);
        return mapToResponse(variant);
    }

    @Override
    @Transactional
    public void deleteVariant(Long variantId) {
        ProductVariant variant = variantRepository.findByIdAndIsDeletedFalse(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy biến thể với id: " + variantId));
        variant.setIsDeleted(true);
        variantRepository.save(variant);
    }

    private ProductVariantResponse mapToResponse(ProductVariant variant) {
        return ProductVariantResponse.builder()
                .id(variant.getId())
                .productId(variant.getProduct().getId())
                .sku(variant.getSku())
                .variantName(variant.getVariantName())
                .price(variant.getPrice())
                .discountPrice(variant.getDiscountPrice())
                .volume(variant.getVolume())
                .color(variant.getColor())
                .barcode(variant.getBarcode())
                .isDefault(variant.getIsDefault())
                .isActive(variant.getIsActive())
                .createdAt(variant.getCreatedAt())
                .updatedAt(variant.getUpdatedAt())
                .build();
    }
}
