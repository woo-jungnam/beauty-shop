package com.core.beautyshop.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WarehouseStockRequest {
    @NotNull(message = "ID biến thể sản phẩm không được để trống")
    private Long productVariantId;

    @NotNull(message = "Số lượng không được để trống")
    @Min(value = 0, message = "Số lượng phải lớn hơn hoặc bằng 0")
    private Integer quantity;

    @Min(value = 0, message = "Số lượng đặt trước phải lớn hơn hoặc bằng 0")
    private Integer reservedQuantity = 0;
}