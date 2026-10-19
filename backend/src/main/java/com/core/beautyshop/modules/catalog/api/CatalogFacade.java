package com.core.beautyshop.modules.catalog.api;

import com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto;

import java.util.Collection;
import java.util.Map;

public interface CatalogFacade {
    ProductVariantSummaryDto getVariantSummaryById(Long variantId);
    Map<Long, ProductVariantSummaryDto> getVariantSummariesByIds(Collection<Long> variantIds);
    ProductVariantSummaryDto getVariantSummaryForInventory(Long variantId);
    Map<Long, ProductVariantSummaryDto> getVariantSummariesForInventory(Collection<Long> variantIds);
    boolean variantExistsById(Long variantId);
    boolean productExistsById(Long productId);
    void updateProductRating(Long productId, double averageRating, int totalReviews);
    void lockProductForRating(Long productId);
}
