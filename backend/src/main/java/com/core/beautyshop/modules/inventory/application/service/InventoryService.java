package com.core.beautyshop.modules.inventory.application.service;

import com.core.beautyshop.modules.inventory.api.dto.ExpiringVariantStockDto;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface InventoryService {
    boolean isStockAvailable(Long variantId, int requiredQuantity);
    int getAvailableQuantity(Long variantId);
    int getAvailableQuantity(Long variantId, LocalDate businessDate);
    Map<Long, Integer> getAvailableQuantities(Collection<Long> variantIds);
    List<ExpiringVariantStockDto> getExpiringVariantStocks(LocalDate businessDate, int thresholdDays, int page, int size);
}
