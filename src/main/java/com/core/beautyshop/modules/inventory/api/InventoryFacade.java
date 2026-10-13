package com.core.beautyshop.modules.inventory.api;

import com.core.beautyshop.modules.inventory.api.dto.ExpiringVariantStockDto;
import jakarta.persistence.criteria.CommonAbstractCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

import java.util.List;

public interface InventoryFacade {
    boolean isStockAvailable(Long variantId, int requiredQuantity);
    int getAvailableQuantity(Long variantId);
    List<ExpiringVariantStockDto> getExpiringVariantStocks(int thresholdDays, int limit);
    /**
     * Adds the inventory-owned availability condition to the caller's query, including count queries.
     * Availability is sum(quantity-reserved)>0 across sellable, unexpired batches in active warehouses;
     * quarantine is a separate bucket. Uses the same database CURRENT_DATE boundary as stock reads.
     * This builds an expression only and never acquires reservation locks or exposes inventory entities.
     */
    Predicate hasAvailableStock(CriteriaBuilder builder, CommonAbstractCriteria query, Expression<Long> variantId);
    void reserveStock(String orderNumber, Long variantId, int quantityToReserve);
    void releaseStock(String orderNumber, Long variantId, int quantityToRelease);
    void deductStock(String orderNumber, Long variantId, int quantityToDeduct);
    void returnStock(String orderNumber, Long variantId, int quantityToReturn);
}
