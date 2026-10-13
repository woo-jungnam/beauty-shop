package com.core.beautyshop.modules.inventory.domain;

import com.core.beautyshop.modules.inventory.domain.enums.InventoryTransactionType;
import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "inventory_transactions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InventoryTransaction extends Base {
    @Column(name = "warehouse_stock_id", nullable = false)
    private Long warehouseStockId;
    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;
    @Column(name = "product_variant_id", nullable = false)
    private Long productVariantId;
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private InventoryTransactionType transactionType;
    @Column(nullable = false)
    private Integer quantity;
    @Column(name = "quantity_before", nullable = false)
    private Integer quantityBefore;
    @Column(name = "quantity_after", nullable = false)
    private Integer quantityAfter;
    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;
    @Column(name = "reference_type", length = 30)
    private String referenceType;
    @Column(name = "reference_id", length = 100)
    private String referenceId;
    @Column(length = 500)
    private String note;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
