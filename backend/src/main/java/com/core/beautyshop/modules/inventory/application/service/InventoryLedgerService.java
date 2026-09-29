package com.core.beautyshop.modules.inventory.application.service;

import com.core.beautyshop.modules.inventory.domain.*;
import com.core.beautyshop.modules.inventory.domain.enums.InventoryTransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class InventoryLedgerService {
    private final InventoryTransactionRepository repository;

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
        return page.map(LedgerView::from);
    }

    public record LedgerView(Long id, Long stockId, Long warehouseId, Long productVariantId,
            InventoryTransactionType type, Integer quantity, Integer quantityBefore, Integer quantityAfter,
            BigDecimal unitCost, String referenceType, String referenceId, String note, Instant occurredAt) {
        static LedgerView from(InventoryTransaction tx) {
            return new LedgerView(tx.getId(), tx.getWarehouseStockId(), tx.getWarehouseId(), tx.getProductVariantId(),
                    tx.getTransactionType(), tx.getQuantity(), tx.getQuantityBefore(), tx.getQuantityAfter(), tx.getUnitCost(),
                    tx.getReferenceType(), tx.getReferenceId(), tx.getNote(), tx.getOccurredAt());
        }
    }
}
