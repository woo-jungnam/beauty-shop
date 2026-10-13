package com.core.beautyshop.modules.inventory.application.service;

import com.core.beautyshop.modules.inventory.api.dto.ExpiringVariantStockDto;
import java.util.List;

public interface InventoryService {
    boolean isStockAvailable(Long variantId, int requiredQuantity);
    int getAvailableQuantity(Long variantId);
    List<ExpiringVariantStockDto> getExpiringVariantStocks(int thresholdDays, int limit);
}
