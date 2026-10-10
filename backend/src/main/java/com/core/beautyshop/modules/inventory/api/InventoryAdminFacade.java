package com.core.beautyshop.modules.inventory.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface InventoryAdminFacade {
    void validateWarehouse(Long warehouseId);
    void receive(Long warehouseId, Long variantId, int quantity, BigDecimal costPrice,
                 String batchCode, LocalDate expirationDate, String referenceType, String referenceId);
}
