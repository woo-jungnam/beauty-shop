package com.core.beautyshop.modules.inventory.application.service;
import com.core.beautyshop.modules.inventory.domain.*;
import com.core.beautyshop.modules.inventory.api.exception.InsufficientStockException;
import com.core.beautyshop.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StockAllocationService {
    private final WarehouseStockRepository stocks;
    private final StockAllocationRepository allocations;

    @Transactional
    public void reserve(String orderNumber, Long variantId, int quantity) {
        validate(orderNumber, quantity);
        List<StockAllocation> existing = allocations.lockAllocations(orderNumber, variantId);
        if (!existing.isEmpty()) {
            if (existing.stream().mapToInt(StockAllocation::getQuantity).sum() == quantity
                    && existing.stream().allMatch(a -> a.getStatus() == StockAllocation.Status.RESERVED)) return;
            throw new BusinessException("Allocation already exists with different quantity or state");
        }
        int remaining = quantity;
        for (WarehouseStock stock : stocks.lockAvailableBatches(variantId)) {
            int take = Math.min(remaining, Math.max(0, stock.getQuantity() - stock.getReservedQuantity()));
            if (take == 0) continue;
            stock.setReservedQuantity(stock.getReservedQuantity() + take);
            StockAllocation allocation = new StockAllocation();
            allocation.setOrderNumber(orderNumber);
            allocation.setVariantId(variantId);
            allocation.setStockId(stock.getId());
            allocation.setQuantity(take);
            allocations.save(allocation);
            remaining -= take;
            if (remaining == 0) break;
        }
        if (remaining != 0) throw new InsufficientStockException("Insufficient stock for variant " + variantId);
    }

    @Transactional
    public void transition(String orderNumber, Long variantId, int quantity, StockAllocation.Status target) {
        validate(orderNumber, quantity);
        List<StockAllocation> rows = allocations.lockAllocations(orderNumber, variantId);
        if (rows.isEmpty() || rows.stream().mapToInt(StockAllocation::getQuantity).sum() != quantity) {
            throw new BusinessException("Missing order batch allocation; reconcile legacy inventory before changing this order");
        }
        for (StockAllocation allocation : rows) {
            if (allocation.getStatus() == target) continue;
            StockAllocation.Status expected = target == StockAllocation.Status.QUARANTINED
                    ? StockAllocation.Status.DEDUCTED : StockAllocation.Status.RESERVED;
            if (allocation.getStatus() != expected) throw new BusinessException("Invalid allocation transition");
            WarehouseStock stock = stocks.findByIdForUpdate(allocation.getStockId())
                    .orElseThrow(() -> new BusinessException("Allocated batch no longer exists"));
            int amount = allocation.getQuantity();
            if (target == StockAllocation.Status.QUARANTINED) {
                // Returned goods retain their original batch/expiry and cannot be sold before inspection.
                stock.setQuarantinedQuantity(Math.addExact(stock.getQuarantinedQuantity(), amount));
            } else {
                if (stock.getReservedQuantity() < amount || stock.getQuantity() < amount) {
                    throw new BusinessException("Allocated stock is inconsistent");
                }
                if (target == StockAllocation.Status.DEDUCTED) {
                    if (stock.getExpirationDate() != null && !stock.getExpirationDate().isAfter(java.time.LocalDate.now())) {
                        throw new BusinessException("Allocated batch expired; reconcile before fulfilling order");
                    }
                    stock.setQuantity(stock.getQuantity() - amount);
                }
                stock.setReservedQuantity(stock.getReservedQuantity() - amount);
            }
            allocation.setStatus(target);
        }
    }

    private void validate(String orderNumber, int quantity) {
        if (orderNumber == null || orderNumber.isBlank() || quantity <= 0) {
            throw new IllegalArgumentException("Order and positive quantity are required");
        }
    }
}
