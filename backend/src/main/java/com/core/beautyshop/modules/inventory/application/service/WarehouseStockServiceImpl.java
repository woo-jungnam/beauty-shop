package com.core.beautyshop.modules.inventory.application.service;

import com.core.beautyshop.modules.catalog.api.CatalogFacade;
import com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto;
import com.core.beautyshop.modules.inventory.application.dto.request.WarehouseStockRequest;
import com.core.beautyshop.modules.inventory.application.dto.response.WarehouseStockResponse;
import com.core.beautyshop.modules.inventory.domain.Warehouse;
import com.core.beautyshop.modules.inventory.domain.WarehouseStock;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.shared.exception.BusinessException;
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
    private final InventoryLedgerService ledgerService;

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

        Optional<WarehouseStock> existingStock = stockRepository.findByWarehouseIdAndProductVariantIdAndBatchCode(warehouseId, variant.getId(), request.getBatchCode());

        WarehouseStock stock;
        int quantityBefore;
        if (existingStock.isPresent()) {
            stock = stockRepository.findByIdForUpdate(existingStock.get().getId()).orElseThrow();
            if (request.getQuantity() < stock.getReservedQuantity()
                    || request.getReservedQuantity() != null && !request.getReservedQuantity().equals(stock.getReservedQuantity())) {
                throw new com.core.beautyshop.shared.exception.BusinessException("Cannot overwrite order reservations");
            }
            quantityBefore = stock.getQuantity();
            stock.setQuantity(request.getQuantity());
        } else {
            if (request.getReservedQuantity() != null && request.getReservedQuantity() != 0) {
                throw new com.core.beautyshop.shared.exception.BusinessException("Reservations must be created by checkout");
            }
            quantityBefore = 0;
            stock = WarehouseStock.builder()
                    .warehouse(warehouse)
                    .productVariantId(variant.getId())
                    .quantity(request.getQuantity())
                    .reservedQuantity(request.getReservedQuantity() != null ? request.getReservedQuantity() : 0)
                    .batchCode(request.getBatchCode())
                    .expirationDate(request.getExpirationDate())
                    .minQuantity(request.getMinQuantity())
                    .maxQuantity(request.getMaxQuantity())
                    .location(request.getLocation())
                    .costPrice(request.getCostPrice())
                    .build();
        }

        stock.setExpirationDate(request.getExpirationDate());
        stock.setMinQuantity(request.getMinQuantity());
        stock.setMaxQuantity(request.getMaxQuantity());
        stock.setLocation(request.getLocation());
        stock.setCostPrice(request.getCostPrice());

        stock = stockRepository.save(stock);
        int delta = stock.getQuantity() - quantityBefore;
        if (delta != 0) {
            ledgerService.record(stock, delta > 0 ? com.core.beautyshop.modules.inventory.domain.enums.InventoryTransactionType.RECEIPT
                            : com.core.beautyshop.modules.inventory.domain.enums.InventoryTransactionType.ADJUSTMENT,
                    delta, quantityBefore, stock.getQuantity(), "MANUAL_STOCK", String.valueOf(stock.getId()), "Admin stock update");
        }
        return mapToResponse(stock);
    }

    @Override
    @Transactional
    public void deleteStock(Long stockId) {
        WarehouseStock stock = stockRepository.findByIdForUpdate(stockId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kho hàng với id: " + stockId));
        if (valueOrZero(stock.getReservedQuantity()) > 0) {
            throw new BusinessException("Không thể xóa lô hàng đang được giữ cho đơn hàng");
        }
        if (valueOrZero(stock.getQuarantinedQuantity()) > 0) {
            throw new BusinessException("Không thể xóa lô hàng đang bị cách ly");
        }
        int quantityBefore = stock.getQuantity();
        if (quantityBefore > 0) {
            stock.setQuantity(0);
            ledgerService.record(stock, com.core.beautyshop.modules.inventory.domain.enums.InventoryTransactionType.DISPOSAL,
                    -quantityBefore, quantityBefore, 0, "STOCK_DELETE", String.valueOf(stock.getId()), "Stock batch deactivated");
        }
        stock.setIsDeleted(true);
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
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
                .quarantinedQuantity(stock.getQuarantinedQuantity())
                .batchCode(stock.getBatchCode())
                .minQuantity(stock.getMinQuantity())
                .maxQuantity(stock.getMaxQuantity())
                .location(stock.getLocation())
                .costPrice(stock.getCostPrice())
                .expirationDate(stock.getExpirationDate())
                .createdAt(stock.getCreatedAt())
                .updatedAt(stock.getUpdatedAt())
                .build();
    }
}
