package com.core.beautyshop.modules.inventory.application.service;

import com.core.beautyshop.modules.catalog.api.CatalogFacade;
import com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto;
import com.core.beautyshop.modules.inventory.application.dto.request.WarehouseStockRequest;
import com.core.beautyshop.modules.inventory.application.dto.response.WarehouseStockResponse;
import com.core.beautyshop.modules.inventory.domain.Warehouse;
import com.core.beautyshop.modules.inventory.domain.WarehouseStock;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.modules.inventory.domain.WarehouseRepository;
import com.core.beautyshop.modules.inventory.domain.WarehouseStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WarehouseStockServiceImpl implements WarehouseStockService {

    private final WarehouseStockRepository stockRepository;
    private final WarehouseRepository warehouseRepository;
    private final CatalogFacade catalogFacade;

    @Override
    public List<WarehouseStockResponse> getStocksByWarehouseId(Long warehouseId) {
        return stockRepository.findByWarehouseId(warehouseId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public WarehouseStockResponse addOrUpdateStock(Long warehouseId, WarehouseStockRequest request) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kho với id: " + warehouseId));
        
        ProductVariantSummaryDto variant = catalogFacade.getVariantSummaryById(request.getProductVariantId());

        Optional<WarehouseStock> existingStock = stockRepository.findByWarehouseIdAndProductVariantId(warehouseId, variant.getId());

        WarehouseStock stock;
        if (existingStock.isPresent()) {
            stock = existingStock.get();
            stock.setQuantity(request.getQuantity());
            stock.setReservedQuantity(request.getReservedQuantity() != null ? request.getReservedQuantity() : stock.getReservedQuantity());
        } else {
            stock = WarehouseStock.builder()
                    .warehouse(warehouse)
                    .productVariantId(variant.getId())
                    .quantity(request.getQuantity())
                    .reservedQuantity(request.getReservedQuantity() != null ? request.getReservedQuantity() : 0)
                    .build();
        }

        stock = stockRepository.save(stock);
        return mapToResponse(stock);
    }

    @Override
    @Transactional
    public void deleteStock(Long stockId) {
        if (!stockRepository.existsById(stockId)) {
            throw new ResourceNotFoundException("Không tìm thấy kho hàng với id: " + stockId);
        }
        stockRepository.deleteById(stockId);
    }

    private WarehouseStockResponse mapToResponse(WarehouseStock stock) {
        String sku = catalogFacade.findVariantSummaryById(stock.getProductVariantId())
                .map(ProductVariantSummaryDto::getSku)
                .orElse(null);

        return WarehouseStockResponse.builder()
                .id(stock.getId())
                .warehouseId(stock.getWarehouse().getId())
                .productVariantId(stock.getProductVariantId())
                .sku(sku)
                .quantity(stock.getQuantity())
                .reservedQuantity(stock.getReservedQuantity())
                .createdAt(stock.getCreatedAt())
                .updatedAt(stock.getUpdatedAt())
                .build();
    }
}
