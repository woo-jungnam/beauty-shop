package com.core.beautyshop.modules.inventory.api;

import io.swagger.v3.oas.annotations.tags.Tag;


import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.modules.inventory.application.dto.request.CreateWarehouseRequest;
import com.core.beautyshop.modules.inventory.application.dto.request.UpdateWarehouseRequest;
import com.core.beautyshop.modules.inventory.application.dto.request.WarehouseStockRequest;
import com.core.beautyshop.modules.inventory.application.dto.response.WarehouseResponse;
import com.core.beautyshop.modules.inventory.application.dto.response.WarehouseStockResponse;
import com.core.beautyshop.modules.inventory.application.service.WarehouseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.core.beautyshop.modules.inventory.application.service.WarehouseStockService;
@Tag(name = "Quản lý kho", description = "API quản lý kho")
@RestController
@RequestMapping("/api/v1/admin/warehouses")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;
    private final WarehouseStockService warehouseStockService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<WarehouseResponse>>> getAllWarehouses() {
        List<WarehouseResponse> warehouses = warehouseService.getAllWarehouses();
        return ResponseEntity.ok(ApiResponse.success(warehouses));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WarehouseResponse>> getWarehouseById(@PathVariable Long id) {
        WarehouseResponse warehouse = warehouseService.getWarehouseById(id);
        return ResponseEntity.ok(ApiResponse.success(warehouse));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WarehouseResponse>> createWarehouse(
            @Valid @RequestBody CreateWarehouseRequest request) {
        WarehouseResponse warehouse = warehouseService.createWarehouse(request);
        return ResponseEntity.status(201).body(ApiResponse.created(warehouse, "Warehouse created successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<WarehouseResponse>> updateWarehouse(
            @PathVariable Long id,
            @Valid @RequestBody UpdateWarehouseRequest request) {
        WarehouseResponse warehouse = warehouseService.updateWarehouse(id, request);
        return ResponseEntity.ok(ApiResponse.success(warehouse));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteWarehouse(@PathVariable Long id) {
        warehouseService.deleteWarehouse(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{id}/stocks")
    public ResponseEntity<ApiResponse<List<WarehouseStockResponse>>> getWarehouseStocks(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                warehouseStockService.getStocksByWarehouseId(id)));
    }

    @PostMapping("/{id}/stocks")
    public ResponseEntity<ApiResponse<WarehouseStockResponse>> addOrUpdateStock(
            @PathVariable Long id,
            @Valid @RequestBody WarehouseStockRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                warehouseStockService.addOrUpdateStock(id, request)));
    }

    @DeleteMapping("/stocks/{stockId}")
    public ResponseEntity<ApiResponse<Void>> deleteStock(@PathVariable Long stockId) {
        warehouseStockService.deleteStock(stockId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
