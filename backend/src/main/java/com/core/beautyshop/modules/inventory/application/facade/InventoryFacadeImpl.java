package com.core.beautyshop.modules.inventory.application.facade;

import com.core.beautyshop.modules.inventory.api.InventoryFacade;
import com.core.beautyshop.modules.inventory.application.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryFacadeImpl implements InventoryFacade {

    private final InventoryService inventoryService;

    @Override
    public boolean isStockAvailable(Long variantId, int requiredQuantity) {
        return inventoryService.isStockAvailable(variantId, requiredQuantity);
    }

    @Override
    public int getAvailableQuantity(Long variantId) {
        return inventoryService.getAvailableQuantity(variantId);
    }

    @Override
    public void reserveStock(Long variantId, int quantityToReserve) {
        inventoryService.reserveStock(variantId, quantityToReserve);
    }

    @Override
    public void releaseStock(Long variantId, int quantityToRelease) {
        inventoryService.releaseStock(variantId, quantityToRelease);
    }

    @Override
    public void deductStock(Long variantId, int quantityToDeduct) {
        inventoryService.deductStock(variantId, quantityToDeduct);
    }
}
