package com.core.beautyshop.modules.inventory.application.service;

import com.core.beautyshop.modules.inventory.application.dto.request.WarehouseStockRequest;
import com.core.beautyshop.modules.inventory.application.dto.response.WarehouseStockResponse;

import java.util.List;

public interface WarehouseStockService {
    List<WarehouseStockResponse> getStocksByWarehouseId(Long warehouseId);
    WarehouseStockResponse addOrUpdateStock(Long warehouseId, WarehouseStockRequest request);
    void deleteStock(Long stockId);
}
