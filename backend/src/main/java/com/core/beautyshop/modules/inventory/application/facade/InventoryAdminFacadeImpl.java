package com.core.beautyshop.modules.inventory.application.facade;

import com.core.beautyshop.modules.inventory.api.InventoryAdminFacade;
import com.core.beautyshop.modules.inventory.application.dto.request.WarehouseStockRequest;
import com.core.beautyshop.modules.inventory.application.service.InventoryBatchService;
import com.core.beautyshop.modules.inventory.application.service.InventoryLedgerService;
import com.core.beautyshop.modules.inventory.application.service.WarehouseStockService;
import com.core.beautyshop.modules.inventory.domain.WarehouseRepository;
import com.core.beautyshop.modules.inventory.domain.enums.InventoryTransactionType;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class InventoryAdminFacadeImpl implements InventoryAdminFacade {
    private final WarehouseStockService service;
    private final WarehouseRepository warehouses;
    private final InventoryBatchService batches;
    private final InventoryLedgerService ledger;

    @Override @Transactional
    public Long receiveBatch(Long warehouseId, Long variantId, int quantity, BigDecimal cost, String batchCode, LocalDate expiry, String reference) {
        WarehouseStockRequest request = new WarehouseStockRequest();
        request.setProductVariantId(variantId);
        request.setQuantity(quantity);
        request.setCostPrice(cost);
        request.setBatchCode(batchCode);
        request.setExpirationDate(expiry);
        return service.receiveStock(warehouseId, request, "PURCHASE_RECEIPT", reference).getId();
    }

    @Override @Transactional
    public void returnToSupplier(Long warehouseId, Long variantId, String batchCode, int quantity, String reference) {
        batches.lockWarehouse(warehouseId);
        var stock = batches.lockBatch(warehouseId, variantId, batchCode).orElseThrow(() -> new BusinessException("Supplier return batch not found"));
        if (quantity <= 0 || quantity > stock.getQuantity() - stock.getReservedQuantity()) {
            throw new BusinessException("Supplier return exceeds available unreserved stock");
        }
        int before = stock.getQuantity();
        stock.setQuantity(before - quantity);
        ledger.record(stock, InventoryTransactionType.SUPPLIER_RETURN, -quantity, before, stock.getQuantity(), "SUPPLIER_RETURN", reference, "Goods returned to supplier");
    }

    @Override
    @Transactional(readOnly = true)
    public void validateWarehouse(Long warehouseId) {
        if (warehouseId == null) throw new BusinessException("Warehouse is required");
        var warehouse = warehouses.findByIdAndIsDeletedFalse(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found: " + warehouseId));
        if (!Boolean.TRUE.equals(warehouse.getIsActive()))
            throw new BusinessException("Warehouse is inactive");
    }

    @Override
    public void receive(Long warehouseId, Long variantId, int quantity, BigDecimal costPrice,
                        String batchCode, LocalDate expirationDate, String referenceType, String referenceId) {
        WarehouseStockRequest request = new WarehouseStockRequest();
        request.setProductVariantId(variantId);
        request.setQuantity(quantity);
        request.setReservedQuantity(0);
        request.setCostPrice(costPrice);
        request.setBatchCode(batchCode);
        request.setExpirationDate(expirationDate);
        service.receiveStock(warehouseId, request, referenceType, referenceId);
    }
}
