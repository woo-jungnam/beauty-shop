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
    Predicate hasAvailableStock(CriteriaBuilder builder, CommonAbstractCriteria query, Expression<Long> variantId, LocalDate businessDate);
    void reserveStock(String orderNumber, Long variantId, int quantityToReserve);
    void releaseStock(String orderNumber, Long variantId, int quantityToRelease);
    void deductStock(String orderNumber, Long variantId, int quantityToDeduct);
    void returnStock(String orderNumber, Long variantId, int quantityToReturn);
    void validateForDispatch(String orderNumber, Long variantId, int quantity);
    void validateDeliveredAllocation(String orderNumber, Long variantId, int quantity);
    void returnPartialStock(String orderNumber, Long variantId, int quantity);
    void replaceAllocation(String orderNumber, Long variantId, int quantity);
    com.core.beautyshop.modules.inventory.api.dto.InventoryConsumptionResult consumeSpaUsage(String reference, Long variantId, Long warehouseId, int quantity);
    java.util.List<com.core.beautyshop.modules.inventory.api.dto.SpaMaterialOption> listSpaMaterialOptions();
}
