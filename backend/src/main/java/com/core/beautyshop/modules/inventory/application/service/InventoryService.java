package com.core.beautyshop.modules.inventory.application.service;
public interface InventoryService {
    boolean isStockAvailable(Long variantId, int requiredQuantity);
    int getAvailableQuantity(Long variantId);
}
