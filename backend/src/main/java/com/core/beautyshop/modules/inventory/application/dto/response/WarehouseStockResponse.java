package com.core.beautyshop.modules.inventory.application.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.math.BigDecimal;

@Data
@Builder
public class WarehouseStockResponse {
    private Long id;
    private Long warehouseId;
    private Long productVariantId;
    private String sku;
    private Integer quantity;
    private Integer reservedQuantity;
    private Integer quarantinedQuantity;
    private java.time.LocalDate expirationDate;
    private String batchCode;
    private Integer minQuantity;
    private Integer maxQuantity;
    private String location;
    private BigDecimal costPrice;
    private Instant createdAt;
    private Instant updatedAt;
}
