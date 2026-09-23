package com.core.beautyshop.modules.inventory.api;

public interface InventoryFacade {
    boolean isStockAvailable(Long variantId, int requiredQuantity);
    int getAvailableQuantity(Long variantId);
    void reserveStock(String orderNumber, Long variantId, int quantityToReserve);
    void releaseStock(String orderNumber, Long variantId, int quantityToRelease);
    void deductStock(String orderNumber, Long variantId, int quantityToDeduct);
    void returnStock(String orderNumber, Long variantId, int quantityToReturn);
}
