package com.core.beautyshop.service.product;

import com.core.beautyshop.dto.request.ProductVariantRequest;
import com.core.beautyshop.dto.response.ProductVariantResponse;
import com.core.beautyshop.entities.product.Product;
import com.core.beautyshop.entities.product.ProductVariant;
import com.core.beautyshop.exception.BusinessException;
import com.core.beautyshop.exception.ResourceNotFoundException;
import com.core.beautyshop.repository.ProductRepository;
import com.core.beautyshop.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductVariantServiceImpl implements ProductVariantService {

    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;

    @Override
    public List<ProductVariantResponse> getVariantsByProductId(Long productId) {
        return variantRepository.findByProductIdAndIsDeletedFalse(productId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ProductVariantResponse getVariantById(Long variantId) {
        ProductVariant variant = variantRepository.findByIdAndIsDeletedFalse(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy biến thể với id: " + variantId));
        return mapToResponse(variant);
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