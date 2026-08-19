package com.core.beautyshop.modules.inventory.application.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class WarehouseStockResponse {
    private Long id;
    private Long warehouseId;
    private Long productVariantId;
    private String sku;
    private Integer quantity;
    private Integer reservedQuantity;
    private Instant createdAt;
    private Instant updatedAt;
}
