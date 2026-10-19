package com.core.beautyshop.modules.inventory.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface InventoryAdminFacade {
    Long receiveBatch(Long warehouseId,Long variantId,int quantity,BigDecimal cost,String batchCode,LocalDate expiry,String reference);
    void returnToSupplier(Long warehouseId,Long variantId,String batchCode,int quantity,String reference);
    void validateWarehouse(Long warehouseId);
    void receive(Long warehouseId, Long variantId, int quantity, BigDecimal costPrice,
                 String batchCode, LocalDate expirationDate, String referenceType, String referenceId);
}
