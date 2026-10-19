package com.core.beautyshop.modules.inventory.api;

import com.core.beautyshop.modules.inventory.api.dto.ExpiringVariantStockDto;
import jakarta.persistence.criteria.CommonAbstractCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

import java.time.LocalDate;
import java.util.List;

public interface InventoryFacade {
    boolean isStockAvailable(Long variantId, int requiredQuantity);
    int getAvailableQuantity(Long variantId);
    int getAvailableQuantity(Long variantId, LocalDate businessDate);
    List<ExpiringVariantStockDto> getExpiringVariantStocks(LocalDate businessDate, int thresholdDays, int page, int size);
    /**
     * Adds the inventory-owned availability condition to the caller's query, including count queries.
     * Availability is sum(quantity-reserved)>0 across sellable batches in active warehouses;
     * dated batches must expire after businessDate+30. NULL dates retain legacy availability.
     * Quarantine is separate. The caller captures the Vietnamese business date once per operation.
     * This builds an expression only and never acquires reservation locks or exposes inventory entities.
     */
    Predicate hasAvailableStock(CriteriaBuilder builder, CommonAbstractCriteria query, Expression<Long> variantId, LocalDate businessDate);
    void reserveStock(String orderNumber, Long variantId, int quantityToReserve);
    void releaseStock(String orderNumber, Long variantId, int quantityToRelease);
    void deductStock(String orderNumber, Long variantId, int quantityToDeduct);
    void returnStock(String orderNumber, Long variantId, int quantityToReturn);
    void validateForDispatch(String orderNumber, Long variantId, int quantity);
    /** Verifies historical shipment evidence only; never deducts or checks today's expiry. */
    void validateDeliveredAllocation(String orderNumber, Long variantId, int quantity);
    void returnPartialStock(String orderNumber, Long variantId, int quantity);
    void replaceAllocation(String orderNumber, Long variantId, int quantity);
    com.core.beautyshop.modules.inventory.api.dto.InventoryConsumptionResult consumeSpaUsage(String reference, Long variantId, Long warehouseId, int quantity);
    java.util.List<com.core.beautyshop.modules.inventory.api.dto.SpaMaterialOption> listSpaMaterialOptions();
}
