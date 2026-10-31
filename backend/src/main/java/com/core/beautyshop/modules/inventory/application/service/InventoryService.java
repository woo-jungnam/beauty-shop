package com.core.beautyshop.modules.inventory.application.service;

import com.core.beautyshop.modules.inventory.api.dto.ExpiringVariantStockDto;
import java.time.LocalDate;
import java.util.List;

public interface InventoryService {
    boolean isStockAvailable(Long variantId, int requiredQuantity);
    int getAvailableQuantity(Long variantId);
    int getAvailableQuantity(Long variantId, LocalDate businessDate);
    java.util.Map<Long, Integer> getAvailableQuantities(java.util.Collection<Long> variantIds);
    List<ExpiringVariantStockDto> getExpiringVariantStocks(LocalDate businessDate, int thresholdDays, int page, int size);
}
