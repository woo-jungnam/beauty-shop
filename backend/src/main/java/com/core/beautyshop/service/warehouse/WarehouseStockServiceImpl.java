package com.core.beautyshop.service.warehouse;

import com.core.beautyshop.dto.request.WarehouseStockRequest;
import com.core.beautyshop.dto.response.WarehouseStockResponse;
import com.core.beautyshop.entities.product.ProductVariant;
import com.core.beautyshop.entities.warehouse.Warehouse;
import com.core.beautyshop.entities.warehouse.WarehouseStock;
import com.core.beautyshop.exception.ResourceNotFoundException;
import com.core.beautyshop.repository.ProductVariantRepository;
import com.core.beautyshop.repository.WarehouseRepository;
import com.core.beautyshop.repository.WarehouseStockRepository;
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
    private final ProductVariantRepository variantRepository;

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
        
        ProductVariant variant = variantRepository.findByIdAndIsDeletedFalse(request.getProductVariantId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy biến thể sản phẩm với id: " + request.getProductVariantId()));

        Optional<WarehouseStock> existingStock = stockRepository.findByWarehouseIdAndProductVariantId(warehouseId, variant.getId());

        WarehouseStock stock;
        if (existingStock.isPresent()) {
            stock = existingStock.get();
            stock.setQuantity(request.getQuantity());
            stock.setReservedQuantity(request.getReservedQuantity() != null ? request.getReservedQuantity() : stock.getReservedQuantity());
        } else {
            stock = WarehouseStock.builder()
                    .warehouse(warehouse)
                    .productVariant(variant)
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
        return WarehouseStockResponse.builder()
                .id(stock.getId())
                .warehouseId(stock.getWarehouse().getId())
                .productVariantId(stock.getProductVariant().getId())
                .sku(stock.getProductVariant().getSku())
                .quantity(stock.getQuantity())
                .reservedQuantity(stock.getReservedQuantity())
                .createdAt(stock.getCreatedAt())
                .updatedAt(stock.getUpdatedAt())
                .build();
    }
}