package com.core.beautyshop.modules.inventory.application.service;

import com.core.beautyshop.modules.inventory.application.dto.request.CreateWarehouseRequest;
import com.core.beautyshop.modules.inventory.application.dto.request.UpdateWarehouseRequest;
import com.core.beautyshop.modules.inventory.application.dto.response.WarehouseResponse;

import java.util.List;

public interface WarehouseService {

    List<WarehouseResponse> getAllWarehouses();

    WarehouseResponse getWarehouseById(Long id);

    WarehouseResponse createWarehouse(CreateWarehouseRequest request);

    WarehouseResponse updateWarehouse(Long id, UpdateWarehouseRequest request);

    void deleteWarehouse(Long id);
}
