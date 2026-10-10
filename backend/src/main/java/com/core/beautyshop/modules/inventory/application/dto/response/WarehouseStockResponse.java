package com.core.beautyshop.modules.inventory.application.dto.response;

import lombok.Builder;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.math.BigDecimal;

@Data
@Builder
@Schema(description = "Số dư một lô theo kho/SKU/batchCode; tồn bán được và hàng cách ly là hai bucket riêng")
public class WarehouseStockResponse {
    private Long id;
    private Long warehouseId;
    private Long productVariantId;
    private String sku;
    @Schema(description = "Tồn bán được, có gồm phần reserved; không gồm quarantine")
    private Integer quantity;
    @Schema(description = "Phần quantity đã giữ cho order; tồn khả dụng=quantity−reservedQuantity")
    private Integer reservedQuantity;
    @Schema(description = "Hàng trả cách ly chờ inspection; chưa được bán và không nằm trong quantity")
    private Integer quarantinedQuantity;
    @Schema(description = "Hạn dùng YYYY-MM-DD, có thể null")
    private java.time.LocalDate expirationDate;
    @Schema(description = "Mã lô đã trim; chuỗi rỗng đại diện lô không mã")
    private String batchCode;
    private Integer minQuantity;
    private Integer maxQuantity;
    private String location;
    @Schema(description = "Giá vốn lưu cho lô, VND")
    private BigDecimal costPrice;
    @Schema(description = "UTC Instant")
    private Instant createdAt;
    @Schema(description = "UTC Instant")
    private Instant updatedAt;
}
