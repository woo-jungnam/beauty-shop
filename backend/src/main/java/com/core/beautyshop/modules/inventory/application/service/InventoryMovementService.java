package com.core.beautyshop.modules.inventory.application.service;

import com.core.beautyshop.modules.inventory.domain.*;
import com.core.beautyshop.modules.inventory.domain.enums.InventoryTransactionType;
import com.core.beautyshop.shared.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class InventoryMovementService {
    private final WarehouseStockRepository stocks;
    private final WarehouseRepository warehouses;
    private final InventoryLedgerService ledger;
    private final InventoryBatchService batches;

    @Transactional
    public void adjust(Long stockId, int quantityAfter, String reason) {
        if (reason == null || reason.isBlank()) throw new BusinessException("Adjustment reason is required");
        WarehouseStock stock = stocks.findByIdForUpdate(stockId).orElseThrow(() -> new ResourceNotFoundException("Stock not found"));
        int unavailable = value(stock.getReservedQuantity());
        if (Boolean.TRUE.equals(stock.getIsDeleted())) throw new BusinessException("Deleted stock cannot be adjusted");
        if (quantityAfter < unavailable) throw new BusinessException("Quantity cannot be lower than reserved stock");
        int before = stock.getQuantity();
        if (before == quantityAfter) return;
        stock.setQuantity(quantityAfter);
        ledger.record(stock, InventoryTransactionType.ADJUSTMENT, quantityAfter - before, before, quantityAfter,
                "STOCKTAKE", String.valueOf(stockId), reason.trim());
    }

    @Transactional
    public void transfer(Long sourceStockId, Long targetWarehouseId, int quantity, String reason) {
        if (quantity <= 0) throw new BusinessException("Transfer quantity must be greater than zero");
        Long sourceWarehouseId = stocks.findWarehouseIdByStockId(sourceStockId)
                .orElseThrow(() -> new ResourceNotFoundException("Source stock not found"));
        if (targetWarehouseId == null) throw new BusinessException("Target warehouse is required");
        if (sourceWarehouseId.equals(targetWarehouseId)) throw new BusinessException("Source and target warehouses must differ");
        // Same ordering for opposing transfers and receipts prevents warehouse/stock lock inversion.
        Warehouse first = batches.lockWarehouse(Math.min(sourceWarehouseId, targetWarehouseId));
        Warehouse second = batches.lockWarehouse(Math.max(sourceWarehouseId, targetWarehouseId));
        Warehouse targetWarehouse = first.getId().equals(targetWarehouseId) ? first : second;
        WarehouseStock source = stocks.findByIdForUpdate(sourceStockId).orElseThrow(() -> new ResourceNotFoundException("Source stock not found"));
        if (Boolean.TRUE.equals(source.getIsDeleted())) throw new BusinessException("Deleted stock cannot be transferred");
        int available = source.getQuantity() - value(source.getReservedQuantity());
        if (available < quantity) throw new BusinessException("Insufficient available stock for transfer");
        int sourceBefore = source.getQuantity(); source.setQuantity(sourceBefore - quantity);
        ledger.record(source, InventoryTransactionType.TRANSFER_OUT, -quantity, sourceBefore, source.getQuantity(), "TRANSFER", null, reason);

        WarehouseStock target = batches.lockBatch(targetWarehouseId, source.getProductVariantId(), source.getBatchCode())
                .orElseGet(() -> WarehouseStock.builder()
                        .warehouse(targetWarehouse).productVariantId(source.getProductVariantId()).quantity(0).reservedQuantity(0).quarantinedQuantity(0)
                        .minQuantity(source.getMinQuantity()).maxQuantity(source.getMaxQuantity()).location(source.getLocation())
                        .expirationDate(source.getExpirationDate()).batchCode(InventoryBatchService.normalizeBatchCode(source.getBatchCode())).costPrice(source.getCostPrice()).build());
        batches.restoreEmptyBatch(target);
        if (!java.util.Objects.equals(target.getExpirationDate(), source.getExpirationDate()))
            throw new BusinessException("Source and target batch expiration dates differ");
        int targetBefore = target.getQuantity(); target.setQuantity(Math.addExact(targetBefore, quantity)); target = stocks.save(target);
        ledger.record(target, InventoryTransactionType.TRANSFER_IN, quantity, targetBefore, target.getQuantity(), "TRANSFER", null, reason);
    }
    private int value(Integer value) { return value == null ? 0 : value; }
}
