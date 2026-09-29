package com.core.beautyshop.modules.inventory.application.service;

import com.core.beautyshop.modules.catalog.api.CatalogFacade;
import com.core.beautyshop.modules.inventory.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class InventoryAlertService {
    private final WarehouseStockRepository repository;
    private final CatalogFacade catalogFacade;

    @Transactional(readOnly = true)
    public List<StockAlert> lowStock() { return map(repository.findLowStock()); }
    @Transactional(readOnly = true)
    public List<StockAlert> expiringSoon(int days) {
        if (days < 0 || days > 365) throw new IllegalArgumentException("Days must be between 0 and 365");
        LocalDate today = LocalDate.now();
        return map(repository.findExpiringSoon(today, today.plusDays(days)));
    }
    @Transactional(readOnly = true)
    public BigDecimal inventoryValue() { return repository.calculateInventoryValue(); }

    private List<StockAlert> map(List<WarehouseStock> stocks) {
        Map<Long, com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto> variants =
                catalogFacade.getVariantSummariesByIds(stocks.stream().map(WarehouseStock::getProductVariantId).distinct().toList());
        return stocks.stream().map(stock -> {
            var variant = variants.get(stock.getProductVariantId());
            return new StockAlert(stock.getId(), stock.getWarehouse().getId(), stock.getWarehouse().getName(),
                    stock.getProductVariantId(), variant == null ? null : variant.getSku(), stock.getBatchCode(),
                    stock.getQuantity(), stock.getReservedQuantity(), stock.getMinQuantity(), stock.getExpirationDate(), stock.getCostPrice());
        }).toList();
    }

    public record StockAlert(Long stockId, Long warehouseId, String warehouseName, Long productVariantId, String sku,
                             String batchCode, Integer quantity, Integer reservedQuantity, Integer minQuantity,
                             LocalDate expirationDate, BigDecimal costPrice) { }
}
