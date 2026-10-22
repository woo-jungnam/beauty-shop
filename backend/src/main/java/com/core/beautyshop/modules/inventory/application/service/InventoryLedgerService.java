package com.core.beautyshop.modules.inventory.application.service;

import com.core.beautyshop.modules.catalog.api.CatalogFacade;
import com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto;
import com.core.beautyshop.modules.inventory.domain.*;
import com.core.beautyshop.modules.inventory.domain.enums.InventoryTransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryLedgerService {
    private final InventoryTransactionRepository repository;
    private final CatalogFacade catalogFacade;
    private final WarehouseRepository warehouseRepository;
    private final WarehouseStockRepository warehouseStockRepository;

    @Transactional
    public void record(WarehouseStock stock, InventoryTransactionType type, int quantity, int before, int after,
                       String referenceType, String referenceId, String note) {
        repository.save(InventoryTransaction.builder().warehouseStockId(stock.getId())
                .warehouseId(stock.getWarehouse().getId()).productVariantId(stock.getProductVariantId())
                .transactionType(type).quantity(quantity).quantityBefore(before).quantityAfter(after)
                .unitCost(stock.getCostPrice() == null ? BigDecimal.ZERO : stock.getCostPrice())
                .referenceType(referenceType).referenceId(referenceId).note(note).occurredAt(Instant.now()).build());
    }

    @Transactional(readOnly = true)
    public Page<LedgerView> find(Long stockId, Long variantId, Pageable pageable) {
        Page<InventoryTransaction> page = stockId != null ? repository.findByWarehouseStockIdOrderByOccurredAtDesc(stockId, pageable)
                : variantId != null ? repository.findByProductVariantIdOrderByOccurredAtDesc(variantId, pageable)
                : repository.findAllByOrderByOccurredAtDesc(pageable);
        List<Long> variantIds = page.getContent().stream().map(InventoryTransaction::getProductVariantId).distinct().toList();
        List<Long> warehouseIds = page.getContent().stream().map(InventoryTransaction::getWarehouseId).distinct().toList();
        List<Long> stockIds = page.getContent().stream().map(InventoryTransaction::getWarehouseStockId).distinct().toList();
        Map<Long, ProductVariantSummaryDto> variants = catalogFacade.getVariantSummariesForInventory(variantIds);
        Map<Long, String> warehouses = warehouseRepository.findAllById(warehouseIds).stream()
                .collect(Collectors.toMap(Warehouse::getId, Warehouse::getName, (a, b) -> a));
        Map<Long, WarehouseStock> stocks = warehouseStockRepository.findAllById(stockIds).stream()
                .collect(Collectors.toMap(WarehouseStock::getId, s -> s, (a, b) -> a));

        return page.map(tx -> LedgerView.from(tx, variants.get(tx.getProductVariantId()), warehouses.get(tx.getWarehouseId()), stocks.get(tx.getWarehouseStockId())));
    }

    @Transactional(readOnly = true)
    public LedgerView findById(Long id) {
        InventoryTransaction tx = repository.findById(id)
                .orElseThrow(() -> new com.core.beautyshop.shared.exception.ResourceNotFoundException("Không tìm thấy giao dịch kho: " + id));
        ProductVariantSummaryDto variant = catalogFacade.getVariantSummariesForInventory(List.of(tx.getProductVariantId())).get(tx.getProductVariantId());
        String warehouseName = warehouseRepository.findById(tx.getWarehouseId()).map(Warehouse::getName).orElse(null);
        WarehouseStock stock = warehouseStockRepository.findById(tx.getWarehouseStockId()).orElse(null);
        return LedgerView.from(tx, variant, warehouseName, stock);
    }

    @Schema(description = "Movement kho bất biến; quantityBefore/After là bucket tồn bán được, đọc type/note để phân biệt giữ/nhả tồn hoặc quarantine")
    public record LedgerView(Long id, Long stockId, Long warehouseId, String warehouseName, Long productVariantId,
            String sku, String productName, String variantName,
            String batchCode, java.time.LocalDate expirationDate, String location,
            InventoryTransactionType type,
            @Schema(description = "Lượng thao tác có dấu; không phải luôn quantityAfter−quantityBefore (reservation/return quarantine)") Integer quantity,
            Integer quantityBefore, Integer quantityAfter,
            @Schema(description = "Giá vốn VND tại lúc ghi movement") BigDecimal unitCost,
            String referenceType, String referenceId, String note,
            String createdBy,
            @Schema(description = "UTC Instant ghi movement") Instant occurredAt) {
        static LedgerView from(InventoryTransaction tx, ProductVariantSummaryDto variant, String warehouseName, WarehouseStock stock) {
            return new LedgerView(tx.getId(), tx.getWarehouseStockId(), tx.getWarehouseId(), warehouseName, tx.getProductVariantId(),
                    variant == null ? null : variant.getSku(),
                    variant == null ? null : variant.getProductName(),
                    variant == null ? null : variant.getVariantName(),
                    stock == null ? null : stock.getBatchCode(),
                    stock == null ? null : stock.getExpirationDate(),
                    stock == null ? null : stock.getLocation(),
                    tx.getTransactionType(), tx.getQuantity(), tx.getQuantityBefore(), tx.getQuantityAfter(), tx.getUnitCost(),
                    tx.getReferenceType(), tx.getReferenceId(), tx.getNote(),
                    tx.getCreatedBy(),
                    tx.getOccurredAt());
        }
    }
}
