package com.core.beautyshop.modules.inventory.application.service;
import com.core.beautyshop.modules.inventory.domain.WarehouseStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {
    private final WarehouseStockRepository warehouseStockRepository;
    @Override @Transactional(readOnly = true)
    public boolean isStockAvailable(Long variantId, int quantity) {
        return quantity > 0 && getAvailableQuantity(variantId) >= quantity;
    }
    @Override @Transactional(readOnly = true)
    public int getAvailableQuantity(Long variantId) {
        Integer quantity = warehouseStockRepository.getTotalAvailableQuantityForVariant(variantId);
        return quantity == null ? 0 : Math.max(0, quantity);
    }
}
