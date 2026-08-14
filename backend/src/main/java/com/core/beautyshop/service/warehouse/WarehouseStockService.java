package com.core.beautyshop.service.warehouse;

import com.core.beautyshop.dto.request.WarehouseStockRequest;
import com.core.beautyshop.dto.response.WarehouseStockResponse;

import java.util.List;

public interface WarehouseStockService {
    List<WarehouseStockResponse> getStocksByWarehouseId(Long warehouseId);
    WarehouseStockResponse addOrUpdateStock(Long warehouseId, WarehouseStockRequest request);
    void deleteStock(Long stockId);
}