package com.core.beautyshop.modules.catalog.application.facade;

import com.core.beautyshop.modules.catalog.api.CatalogFacade;
import com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto;
import com.core.beautyshop.modules.catalog.domain.ProductVariantRepository;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CatalogFacadeImpl implements CatalogFacade {

    private final ProductVariantRepository productVariantRepository;
    private final com.core.beautyshop.modules.catalog.domain.ProductRepository productRepository;

    @Override
    public ProductVariantSummaryDto getVariantSummaryById(Long variantId) {
        if (variantId == null) {
            throw new ResourceNotFoundException("ID biến thể không được để trống");
        }
        return productVariantRepository.findVariantSummaryByIdDto(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy biến thể sản phẩm với id: " + variantId));
    }

    @Override
    public Map<Long, ProductVariantSummaryDto> getVariantSummariesByIds(Collection<Long> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return productVariantRepository.findVariantSummariesByIds(variantIds).stream()
                .collect(Collectors.toMap(ProductVariantSummaryDto::getId, dto -> dto, (existing, replacing) -> existing));
    }

    @Override
    public ProductVariantSummaryDto getVariantSummaryForInventory(Long variantId) {
        if (variantId == null) throw new ResourceNotFoundException("ID biến thể không được để trống");
        return productVariantRepository.findVariantSummaryForInventory(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy biến thể sản phẩm với id: " + variantId));
    }

    @Override
    public Map<Long, ProductVariantSummaryDto> getVariantSummariesForInventory(Collection<Long> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) return Collections.emptyMap();
        return productVariantRepository.findVariantSummariesForInventory(variantIds).stream()
                .collect(Collectors.toMap(ProductVariantSummaryDto::getId, dto -> dto));
    }

    @Override
    public java.util.List<ProductVariantSummaryDto> getVariantSummariesForInventoryBySkus(Collection<String> skus) {
        if (skus == null || skus.isEmpty()) return java.util.List.of();
        var exactSkus = new java.util.HashSet<>(skus);
        return productVariantRepository.findVariantSummariesForInventoryBySkus(exactSkus).stream()
                .filter(dto -> exactSkus.contains(dto.getSku())).toList();
    }

    @Override
    @Transactional
    public void lockProductForRating(Long productId) {
        productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
    }

    @Override
    public boolean variantExistsById(Long variantId) {
        if (variantId == null) {
            return false;
        }
        return productVariantRepository.findByIdAndIsDeletedFalse(variantId).isPresent();
    }

    @Override
    public boolean productExistsById(Long productId) {
        return productId != null && productRepository.findByIdAndIsDeletedFalse(productId).isPresent();
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "products_page", allEntries = true),
            @CacheEvict(value = "product_detail", allEntries = true)
    })
    public void updateProductRating(Long productId, double averageRating, int totalReviews) {
        productRepository.findByIdForUpdate(productId).ifPresent(product -> {
            product.setAverageRating(averageRating);
            product.setTotalReviews(totalReviews);
        });
    }
}
