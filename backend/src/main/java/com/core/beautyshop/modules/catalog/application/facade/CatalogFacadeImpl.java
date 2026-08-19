package com.core.beautyshop.modules.catalog.application.facade;

import com.core.beautyshop.modules.catalog.api.CatalogFacade;
import com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto;
import com.core.beautyshop.modules.catalog.domain.ProductVariant;
import com.core.beautyshop.modules.catalog.domain.ProductVariantRepository;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CatalogFacadeImpl implements CatalogFacade {

    private final ProductVariantRepository productVariantRepository;

    @Override
    public Optional<ProductVariantSummaryDto> findVariantSummaryById(Long variantId) {
        if (variantId == null) {
            return Optional.empty();
        }
        return productVariantRepository.findByIdAndIsDeletedFalse(variantId)
                .map(this::mapToSummary);
    }

    @Override
    public ProductVariantSummaryDto getVariantSummaryById(Long variantId) {
        if (variantId == null) {
            throw new ResourceNotFoundException("Variant id cannot be null");
        }
        return productVariantRepository.findByIdAndIsDeletedFalse(variantId)
                .map(this::mapToSummary)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy biến thể sản phẩm với id: " + variantId));
    }

    @Override
    public boolean variantExistsById(Long variantId) {
        if (variantId == null) {
            return false;
        }
        return productVariantRepository.findByIdAndIsDeletedFalse(variantId).isPresent();
    }

    private ProductVariantSummaryDto mapToSummary(ProductVariant variant) {
        return ProductVariantSummaryDto.builder()
                .id(variant.getId())
                .productId(variant.getProduct() != null ? variant.getProduct().getId() : null)
                .productName(variant.getProduct() != null ? variant.getProduct().getName() : null)
                .sku(variant.getSku())
                .variantName(variant.getVariantName())
                .price(variant.getPrice())
                .discountPrice(variant.getDiscountPrice())
                .isActive(variant.getIsActive())
                .build();
    }
}
