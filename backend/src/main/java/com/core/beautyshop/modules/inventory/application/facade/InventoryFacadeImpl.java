package com.core.beautyshop.modules.inventory.application.facade;

import com.core.beautyshop.modules.inventory.api.InventoryFacade;
import com.core.beautyshop.modules.inventory.application.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryFacadeImpl implements InventoryFacade {

    private final InventoryService inventoryService;
    private final com.core.beautyshop.modules.inventory.application.service.StockAllocationService allocations;

    @Override
    public boolean isStockAvailable(Long variantId, int requiredQuantity) {
        return inventoryService.isStockAvailable(variantId, requiredQuantity);
    }

    @Override
    public int getAvailableQuantity(Long variantId) {
        return inventoryService.getAvailableQuantity(variantId);
    }

    @Override
    public void reserveStock(String orderNumber, Long variantId, int quantityToReserve) {
        allocations.reserve(orderNumber, variantId, quantityToReserve);
    }

    @Override
    public void releaseStock(String orderNumber, Long variantId, int quantityToRelease) {
        allocations.transition(orderNumber, variantId, quantityToRelease, com.core.beautyshop.modules.inventory.domain.StockAllocation.Status.RELEASED);
    }

    @Override
    public void deductStock(String orderNumber, Long variantId, int quantityToDeduct) {
        allocations.transition(orderNumber, variantId, quantityToDeduct, com.core.beautyshop.modules.inventory.domain.StockAllocation.Status.DEDUCTED);
    }

    @Override
    public void returnStock(String orderNumber, Long variantId, int quantityToReturn) {
        allocations.transition(orderNumber, variantId, quantityToReturn, com.core.beautyshop.modules.inventory.domain.StockAllocation.Status.QUARANTINED);
    }
}
