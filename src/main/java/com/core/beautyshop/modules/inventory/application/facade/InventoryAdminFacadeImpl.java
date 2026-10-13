package com.core.beautyshop.modules.inventory.application.facade;

import com.core.beautyshop.modules.inventory.api.InventoryAdminFacade;
import com.core.beautyshop.modules.inventory.application.dto.request.WarehouseStockRequest;
import com.core.beautyshop.modules.inventory.application.service.WarehouseStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class InventoryAdminFacadeImpl implements InventoryAdminFacade {
    private final WarehouseStockService service;
    private final com.core.beautyshop.modules.inventory.domain.WarehouseRepository warehouses;

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public void validateWarehouse(Long warehouseId) {
        if (warehouseId == null) throw new com.core.beautyshop.shared.exception.BusinessException("Warehouse is required");
        var warehouse = warehouses.findByIdAndIsDeletedFalse(warehouseId)
                .orElseThrow(() -> new com.core.beautyshop.shared.exception.ResourceNotFoundException("Warehouse not found: " + warehouseId));
        if (!Boolean.TRUE.equals(warehouse.getIsActive()))
            throw new com.core.beautyshop.shared.exception.BusinessException("Warehouse is inactive");
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
