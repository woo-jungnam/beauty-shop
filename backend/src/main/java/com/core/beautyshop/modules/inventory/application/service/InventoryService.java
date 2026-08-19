package com.core.beautyshop.modules.inventory.application.service;

public interface InventoryService {
    /**
     * Checks if the required quantity is available for a variant
     */
    boolean isStockAvailable(Long variantId, int requiredQuantity);

    /**
     * Retrieves the total available quantity (quantity - reserved) across all warehouses
     */
    int getAvailableQuantity(Long variantId);

    /**
     * Reserves stock across warehouses for an order
     */
    void reserveStock(Long variantId, int quantityToReserve);

    /**
     * Releases reserved stock (e.g. if order is cancelled)
     */
    void releaseStock(Long variantId, int quantityToRelease);
}
