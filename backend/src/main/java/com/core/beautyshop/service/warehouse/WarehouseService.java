package com.core.beautyshop.service.warehouse;

import com.core.beautyshop.dto.request.CreateWarehouseRequest;
import com.core.beautyshop.dto.request.UpdateWarehouseRequest;
import com.core.beautyshop.dto.response.WarehouseResponse;

import java.util.List;

public interface WarehouseService {

    List<WarehouseResponse> getAllWarehouses();

    WarehouseResponse getWarehouseById(Long id);

    WarehouseResponse createWarehouse(CreateWarehouseRequest request);

    WarehouseResponse updateWarehouse(Long id, UpdateWarehouseRequest request);

    void deleteWarehouse(Long id);
}
