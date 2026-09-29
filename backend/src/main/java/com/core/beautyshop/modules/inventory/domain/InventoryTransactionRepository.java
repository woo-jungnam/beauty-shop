package com.core.beautyshop.modules.inventory.domain;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {
    Page<InventoryTransaction> findByWarehouseStockIdOrderByOccurredAtDesc(Long stockId, Pageable pageable);
    Page<InventoryTransaction> findByProductVariantIdOrderByOccurredAtDesc(Long variantId, Pageable pageable);
    Page<InventoryTransaction> findAllByOrderByOccurredAtDesc(Pageable pageable);
}
