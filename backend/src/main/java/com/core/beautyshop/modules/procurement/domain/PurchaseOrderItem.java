package com.core.beautyshop.modules.procurement.domain;

import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity @Table(name = "purchase_order_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PurchaseOrderItem extends Base {
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "purchase_order_id", nullable = false) private PurchaseOrder purchaseOrder;
    @Column(name = "product_variant_id", nullable = false) private Long productVariantId;
    @Column(nullable = false) private Integer quantity;
    @Column(name = "received_quantity", nullable = false) @Builder.Default private Integer receivedQuantity = 0;
    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 2) private BigDecimal unitCost;
    @Column(name = "batch_code", length = 100) private String batchCode;
    @Column(name = "expiration_date") private LocalDate expirationDate;
}
