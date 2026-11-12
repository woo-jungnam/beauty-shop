package com.core.beautyshop.modules.promotion.api;

import java.math.BigDecimal;
import java.util.Optional;

public interface FlashSaleFacade {

    record FlashSalePriceResolution(
            Long flashSaleItemId,
            BigDecimal flashPrice,
            boolean isFlashSale,
            Integer remainingQuota,
            Integer remainingUserQuota
    ) {
        public FlashSalePriceResolution(Long flashSaleItemId, BigDecimal flashPrice, boolean isFlashSale, Integer remainingQuota) {
            this(flashSaleItemId, flashPrice, isFlashSale, remainingQuota, remainingQuota);
        }
    }

    record FlashSaleAllocation(
            Long flashSaleItemId,
            BigDecimal flashPrice,
            int allocatedQuantity,
            Long userId
    ) {}

    default Optional<FlashSalePriceResolution> resolveActiveFlashSale(Long variantId) {
        return resolveActiveFlashSale(variantId, null);
    }

    Optional<FlashSalePriceResolution> resolveActiveFlashSale(Long variantId, Long userId);

    Optional<FlashSaleAllocation> tryAllocateFlashSale(Long variantId, int requestedQuantity, Long userId);

    void confirmAllocation(Long orderId, Long flashSaleItemId, Long userId, int quantity);

    void releaseAllocations(Long orderId);
}
