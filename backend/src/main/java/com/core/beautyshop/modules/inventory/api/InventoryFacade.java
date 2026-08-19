package com.core.beautyshop.modules.inventory.api;

public interface InventoryFacade {
    boolean isStockAvailable(Long variantId, int requiredQuantity);
    int getAvailableQuantity(Long variantId);
    void reserveStock(Long variantId, int quantityToReserve);
    void releaseStock(Long variantId, int quantityToRelease);
}
