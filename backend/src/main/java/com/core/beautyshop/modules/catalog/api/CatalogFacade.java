package com.core.beautyshop.modules.catalog.api;

import com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface CatalogFacade {
    Optional<ProductVariantSummaryDto> findVariantSummaryById(Long variantId);
    ProductVariantSummaryDto getVariantSummaryById(Long variantId);
    Map<Long, ProductVariantSummaryDto> getVariantSummariesByIds(Collection<Long> variantIds);
    ProductVariantSummaryDto getVariantSummaryForInventory(Long variantId);
    Map<Long, ProductVariantSummaryDto> getVariantSummariesForInventory(Collection<Long> variantIds);
    boolean variantExistsById(Long variantId);
    void applyDiscountPrice(Long variantId, java.math.BigDecimal discountPrice);
    void deactivateVariant(Long variantId);
    boolean productExistsById(Long productId);
    void updateProductRating(Long productId, double averageRating, int totalReviews);
    void lockProductForRating(Long productId);
}
