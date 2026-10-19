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
    private final BatchRecallRepository recalls;

    public void requireReceivable(Long variantId,String batchCode) {
        // Serialize against recalls across every warehouse, including a new destination batch.
        stocks.lockRecallBatches(variantId,normalizeBatchCode(batchCode));
        if(!recalls.findByVariantIdAndBatchCodeAndActiveTrue(variantId,normalizeBatchCode(batchCode)).isEmpty())
            throw new BusinessException("This batch is under active recall; resolve the recall before receiving sellable stock");
    }

    public Warehouse lockWarehouse(Long id) {
        Warehouse warehouse = lockWarehouseForHistory(id);
        if (Boolean.TRUE.equals(warehouse.getIsDeleted()) || !Boolean.TRUE.equals(warehouse.getIsActive()))
            throw new BusinessException("Kho hàng đã bị xóa hoặc ngưng hoạt động");
        return warehouse;
    }

    public Warehouse lockWarehouseForHistory(Long id) {
        if (id == null) throw new BusinessException("Kho hàng là bắt buộc");
        Warehouse warehouse = warehouses.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kho hàng: " + id));
        // Procurement validation may have loaded this warehouse before the lock was acquired.
        entities.refresh(warehouse, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return warehouse;
    }

    public Optional<WarehouseStock> lockBatch(Long warehouseId, Long variantId, String batchCode) {
        var rows = stocks.lockBatch(warehouseId, variantId, normalizeBatchCode(batchCode));
        if (rows.size() > 1) throw new BusinessException("Dữ liệu lô hàng cũ bị trùng lặp, cần đối soát kho");
        return rows.stream().findFirst();
    }

    public void restoreEmptyBatch(WarehouseStock stock) {
        if (!Boolean.TRUE.equals(stock.getIsDeleted())) return;
        if (stock.getQuantity() != 0 || stock.getReservedQuantity() != 0 || stock.getQuarantinedQuantity() != 0)
            throw new BusinessException("Lô hàng đã xóa vẫn còn tồn kho; cần đối soát trước khi khôi phục");
        stock.setIsDeleted(false);
    }

    public static String normalizeBatchCode(String batchCode) {
        String normalized = batchCode == null ? "" : batchCode.trim();
        if (normalized.length() > 100) throw new BusinessException("Mã lô hàng không được vượt quá 100 ký tự");
        return normalized;
    }
}
