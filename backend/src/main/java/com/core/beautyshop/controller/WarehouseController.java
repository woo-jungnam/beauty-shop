package com.core.beautyshop.controller;

import io.swagger.v3.oas.annotations.tags.Tag;


import com.core.beautyshop.dto.common.ApiResponse;
import com.core.beautyshop.dto.request.CreateWarehouseRequest;
import com.core.beautyshop.dto.request.UpdateWarehouseRequest;
import com.core.beautyshop.dto.response.WarehouseResponse;
import com.core.beautyshop.service.warehouse.WarehouseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Quản lý kho", description = "API quản lý kho")
@RestController
@RequestMapping("/api/v1/admin/warehouses")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;
    private final com.core.beautyshop.service.warehouse.WarehouseStockService warehouseStockService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<WarehouseResponse>>> getAllWarehouses(HttpServletRequest req) {
        List<WarehouseResponse> warehouses = warehouseService.getAllWarehouses();
        return ResponseEntity.ok(ApiResponse.success(warehouses, req.getRequestURI()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WarehouseResponse>> getWarehouseById(
            @PathVariable Long id,
            HttpServletRequest req) {
        WarehouseResponse warehouse = warehouseService.getWarehouseById(id);
        return ResponseEntity.ok(ApiResponse.success(warehouse, req.getRequestURI()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WarehouseResponse>> createWarehouse(
            @Valid @RequestBody CreateWarehouseRequest request,
            HttpServletRequest req) {
        WarehouseResponse warehouse = warehouseService.createWarehouse(request);
        return ResponseEntity.status(201).body(ApiResponse.created(warehouse, "Warehouse created successfully", req.getRequestURI()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<WarehouseResponse>> updateWarehouse(
            @PathVariable Long id,
            @Valid @RequestBody UpdateWarehouseRequest request,
            HttpServletRequest req) {
        WarehouseResponse warehouse = warehouseService.updateWarehouse(id, request);
        return ResponseEntity.ok(ApiResponse.success(warehouse, req.getRequestURI()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteWarehouse(
            @PathVariable Long id,
            HttpServletRequest req) {
        warehouseService.deleteWarehouse(id);
        return ResponseEntity.ok(ApiResponse.success(null, req.getRequestURI()));
    }

    // --- Stock Endpoints ---

    @GetMapping("/{id}/stocks")
    public ResponseEntity<ApiResponse<List<com.core.beautyshop.dto.response.WarehouseStockResponse>>> getWarehouseStocks(
            @PathVariable Long id, HttpServletRequest req) {
        return ResponseEntity.ok(ApiResponse.success(
                warehouseStockService.getStocksByWarehouseId(id), req.getRequestURI()));
    }

    @PostMapping("/{id}/stocks")
    public ResponseEntity<ApiResponse<com.core.beautyshop.dto.response.WarehouseStockResponse>> addOrUpdateStock(
            @PathVariable Long id,
            @Valid @RequestBody com.core.beautyshop.dto.request.WarehouseStockRequest request,
            HttpServletRequest req) {
        return ResponseEntity.ok(ApiResponse.success(
                warehouseStockService.addOrUpdateStock(id, request), req.getRequestURI()));
    }

    @DeleteMapping("/stocks/{stockId}")
    public ResponseEntity<ApiResponse<Void>> deleteStock(
            @PathVariable Long stockId,
            HttpServletRequest req) {
        warehouseStockService.deleteStock(stockId);
        return ResponseEntity.ok(ApiResponse.success(null, req.getRequestURI()));
    }
}
