package com.core.beautyshop.modules.inventory.application.service;

import com.core.beautyshop.modules.inventory.domain.WarehouseStock;
import com.core.beautyshop.modules.inventory.domain.exception.InsufficientStockException;
import com.core.beautyshop.modules.inventory.domain.WarehouseStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final WarehouseStockRepository warehouseStockRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean isStockAvailable(Long variantId, int requiredQuantity) {
        return getAvailableQuantity(variantId) >= requiredQuantity;
    }

    @Override
    @Transactional(readOnly = true)
    public int getAvailableQuantity(Long variantId) {
        Integer available = warehouseStockRepository.getTotalAvailableQuantityForVariant(variantId);
        return available != null ? available : 0;
    }

    @Override
    @Transactional
    public void reserveStock(Long variantId, int quantityToReserve) {
        if (!isStockAvailable(variantId, quantityToReserve)) {
            throw new InsufficientStockException("Không đủ hàng trong kho cho ID biến thể: " + variantId);
        }

        List<WarehouseStock> stocks = warehouseStockRepository.findByProductVariantId(variantId);
        int remainingToReserve = quantityToReserve;

        for (WarehouseStock stock : stocks) {
            int availInStock = stock.getQuantity() - stock.getReservedQuantity();
            if (availInStock > 0) {
                int reserveAmount = Math.min(availInStock, remainingToReserve);
                stock.setReservedQuantity(stock.getReservedQuantity() + reserveAmount);
                warehouseStockRepository.save(stock);
                remainingToReserve -= reserveAmount;
            }
            if (remainingToReserve <= 0) break;
        }
    }

    @Override
    @Transactional
    public void releaseStock(Long variantId, int quantityToRelease) {
        List<WarehouseStock> stocks = warehouseStockRepository.findByProductVariantId(variantId);
        int remainingToRelease = quantityToRelease;

        for (WarehouseStock stock : stocks) {
            int reservedInStock = stock.getReservedQuantity();
            if (reservedInStock > 0) {
                int releaseAmount = Math.min(reservedInStock, remainingToRelease);
                stock.setReservedQuantity(stock.getReservedQuantity() - releaseAmount);
                warehouseStockRepository.save(stock);
                remainingToRelease -= releaseAmount;
            }
            if (remainingToRelease <= 0) break;
        }
    }
}
