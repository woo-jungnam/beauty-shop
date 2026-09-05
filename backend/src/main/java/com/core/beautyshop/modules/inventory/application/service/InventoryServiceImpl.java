package com.core.beautyshop.modules.inventory.application.service;

import com.core.beautyshop.modules.inventory.domain.WarehouseStock;
import com.core.beautyshop.modules.inventory.api.exception.InsufficientStockException;
import com.core.beautyshop.modules.inventory.domain.WarehouseStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
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
        List<WarehouseStock> stocks = warehouseStockRepository.findByProductVariantIdOrderByIdAsc(variantId);

        int totalAvailable = stocks.stream()
                .mapToInt(s -> Math.max(0, s.getQuantity() - s.getReservedQuantity()))
                .sum();

        if (totalAvailable < quantityToReserve) {
            throw new InsufficientStockException(
                    "Không đủ hàng trong kho cho ID biến thể: " + variantId
                            + " (yêu cầu: " + quantityToReserve + ", khả dụng: " + totalAvailable + ")");
        }

        int remainingToReserve = quantityToReserve;
        for (WarehouseStock stock : stocks) {
            int availInStock = stock.getQuantity() - stock.getReservedQuantity();
            if (availInStock > 0) {
                int reserveAmount = Math.min(availInStock, remainingToReserve);
                int updated = warehouseStockRepository.reserveStockAtomic(stock.getId(), reserveAmount);
                if (updated > 0) {
                    remainingToReserve -= reserveAmount;
                }
            }
            if (remainingToReserve <= 0) break;
        }

        if (remainingToReserve > 0) {
            throw new InsufficientStockException(
                    "Không đủ hàng trong kho cho ID biến thể: " + variantId + " do tồn kho vừa thay đổi. Vui lòng thử lại!");
        }

        log.info("Đã giữ trước (reserve) {} sản phẩm cho biến thể variantId={}", quantityToReserve, variantId);
    }

    @Override
    @Transactional
    public void releaseStock(Long variantId, int quantityToRelease) {
        List<WarehouseStock> stocks = warehouseStockRepository.findByProductVariantIdOrderByIdAsc(variantId);
        int remainingToRelease = quantityToRelease;

        for (WarehouseStock stock : stocks) {
            int reservedInStock = stock.getReservedQuantity();
            if (reservedInStock > 0) {
                int releaseAmount = Math.min(reservedInStock, remainingToRelease);
                int updated = warehouseStockRepository.releaseStockAtomic(stock.getId(), releaseAmount);
                if (updated > 0) {
                    remainingToRelease -= releaseAmount;
                }
            }
            if (remainingToRelease <= 0) break;
        }

        log.info("Đã hoàn lại (release) {} sản phẩm cho biến thể variantId={}", quantityToRelease, variantId);
    }

    @Override
    @Transactional
    public void deductStock(Long variantId, int quantityToDeduct) {
        List<WarehouseStock> stocks = warehouseStockRepository.findByProductVariantIdOrderByIdAsc(variantId);
        int remainingToDeduct = quantityToDeduct;

        for (WarehouseStock stock : stocks) {
            int reservedInStock = stock.getReservedQuantity();
            if (reservedInStock > 0) {
                int deductAmount = Math.min(reservedInStock, remainingToDeduct);
                int updated = warehouseStockRepository.deductStockAtomic(stock.getId(), deductAmount);
                if (updated > 0) {
                    remainingToDeduct -= deductAmount;
                }
            }
            if (remainingToDeduct <= 0) break;
        }

        log.info("Đã trừ tồn kho (deduct) {} sản phẩm cho biến thể variantId={}", quantityToDeduct, variantId);
    }
}
