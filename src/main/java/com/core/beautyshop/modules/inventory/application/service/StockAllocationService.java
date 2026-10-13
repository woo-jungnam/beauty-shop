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
    private final InventoryLedgerService ledger;

    @Transactional
    public void reserve(String orderNumber, Long variantId, int quantity) {
        validate(orderNumber, quantity);
        List<StockAllocation> existing = allocations.lockAllocations(orderNumber, variantId);
        if (!existing.isEmpty()) {
            if (existing.stream().mapToInt(StockAllocation::getQuantity).sum() == quantity
                    && existing.stream().allMatch(a -> a.getStatus() == StockAllocation.Status.RESERVED)) return;
            throw new BusinessException("Phân bổ hàng đã tồn tại với số lượng hoặc trạng thái khác");
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
        if (remaining != 0) throw new InsufficientStockException("Số lượng tồn kho không đủ cho biến thể #" + variantId);
    }

    @Transactional
    public void transition(String orderNumber, Long variantId, int quantity, StockAllocation.Status target) {
        validate(orderNumber, quantity);
        List<StockAllocation> rows = allocations.lockAllocations(orderNumber, variantId);
        if (rows.isEmpty() || rows.stream().mapToInt(StockAllocation::getQuantity).sum() != quantity) {
            throw new BusinessException("Thiếu phân bổ lô hàng cho đơn; cần đối soát tồn kho trước khi thay đổi đơn");
        }
        for (StockAllocation allocation : rows) {
            if (allocation.getStatus() == target) continue;
            StockAllocation.Status expected = target == StockAllocation.Status.QUARANTINED
                    ? StockAllocation.Status.DEDUCTED : StockAllocation.Status.RESERVED;
            if (allocation.getStatus() != expected) throw new BusinessException("Chuyển trạng thái phân bổ kho không hợp lệ");
            WarehouseStock stock = stocks.findByIdForUpdate(allocation.getStockId())
                    .orElseThrow(() -> new BusinessException("Lô hàng được phân bổ không còn tồn tại"));
            int amount = allocation.getQuantity();
            if (target == StockAllocation.Status.QUARANTINED) {
                // Returned goods retain their original batch/expiry and cannot be sold before inspection.
                stock.setQuarantinedQuantity(Math.addExact(stock.getQuarantinedQuantity(), amount));
                ledger.record(stock, com.core.beautyshop.modules.inventory.domain.enums.InventoryTransactionType.RETURN,
                        amount, stock.getQuantity(), stock.getQuantity(), "ORDER", orderNumber, "Returned to quarantine");
            } else {
                if (stock.getReservedQuantity() < amount || stock.getQuantity() < amount) {
                    throw new BusinessException("Dữ liệu tồn kho được phân bổ không nhất quán");
                }
                if (target == StockAllocation.Status.DEDUCTED) {
                    if (stock.getExpirationDate() != null && !stock.getExpirationDate().isAfter(java.time.LocalDate.now())) {
                        throw new BusinessException("Lô hàng được phân bổ đã hết hạn; cần đối soát trước khi hoàn tất đơn");
                    }
                    int before = stock.getQuantity();
                    stock.setQuantity(before - amount);
                    ledger.record(stock, com.core.beautyshop.modules.inventory.domain.enums.InventoryTransactionType.SALE,
                            -amount, before, stock.getQuantity(), "ORDER", orderNumber, "Order fulfilled");
                } else {
                    ledger.record(stock, com.core.beautyshop.modules.inventory.domain.enums.InventoryTransactionType.CANCELLATION,
                            amount, stock.getQuantity(), stock.getQuantity(), "ORDER", orderNumber, "Reservation released");
                }
                stock.setReservedQuantity(stock.getReservedQuantity() - amount);
            }
            allocation.setStatus(target);
        }
    }

    private void validate(String orderNumber, int quantity) {
        if (orderNumber == null || orderNumber.isBlank() || quantity <= 0) {
            throw new IllegalArgumentException("Mã đơn hàng và số lượng dương là bắt buộc");
        }
    }
}
