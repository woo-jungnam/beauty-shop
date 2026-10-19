package com.core.beautyshop.modules.inventory.api.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
public record InventoryConsumptionResult(String reference, Long variantId, Long warehouseId, int quantity, BigDecimal totalCost, List<ConsumptionBatch> batches) {
    public record ConsumptionBatch(Long stockId, String batchCode, LocalDate expirationDate, int quantity, BigDecimal unitCost) {}
}
