package com.core.beautyshop.modules.inventory.application.service;

import com.core.beautyshop.modules.inventory.domain.*;
import com.core.beautyshop.shared.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Optional;

/** All batch creators lock the owning warehouse before looking up a batch, including missing batches. */
@Service
@RequiredArgsConstructor
public class InventoryBatchService {
    private final WarehouseRepository warehouses;
    private final WarehouseStockRepository stocks;
    private final jakarta.persistence.EntityManager entities;

    public Warehouse lockWarehouse(Long id) {
        if (id == null) throw new BusinessException("Warehouse is required");
        Warehouse warehouse = warehouses.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found: " + id));
        // Procurement validation may have loaded this warehouse before the lock was acquired.
        entities.refresh(warehouse, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (Boolean.TRUE.equals(warehouse.getIsDeleted()) || !Boolean.TRUE.equals(warehouse.getIsActive()))
            throw new BusinessException("Warehouse is deleted or inactive");
        return warehouse;
    }

    public Optional<WarehouseStock> lockBatch(Long warehouseId, Long variantId, String batchCode) {
        var rows = stocks.lockBatch(warehouseId, variantId, normalizeBatchCode(batchCode));
        if (rows.size() > 1) throw new BusinessException("Duplicate legacy batch records require reconciliation");
        return rows.stream().findFirst();
    }

    public void restoreEmptyBatch(WarehouseStock stock) {
        if (!Boolean.TRUE.equals(stock.getIsDeleted())) return;
        if (stock.getQuantity() != 0 || stock.getReservedQuantity() != 0 || stock.getQuarantinedQuantity() != 0)
            throw new BusinessException("Deleted batch still contains stock; reconcile before restoring");
        stock.setIsDeleted(false);
    }

    public static String normalizeBatchCode(String batchCode) {
        String normalized = batchCode == null ? "" : batchCode.trim();
        if (normalized.length() > 100) throw new BusinessException("Batch code must not exceed 100 characters");
        return normalized;
    }
}
