package com.core.beautyshop.modules.procurement.domain;

import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Entity @Table(name = "purchase_orders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PurchaseOrder extends Base {
    @Column(name = "order_number", nullable = false, unique = true, length = 50) private String orderNumber;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "supplier_id", nullable = false) private Supplier supplier;
    @Column(name = "warehouse_id", nullable = false) private Long warehouseId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(name = "expected_date") private LocalDate expectedDate;
    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2) private BigDecimal totalAmount;
    @Column(length = 500) private String note;
    @Column(name = "approved_at") private Instant approvedAt;
    @Column(name = "received_at") private Instant receivedAt;
    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true) @Builder.Default
    private List<PurchaseOrderItem> items = new ArrayList<>();
    public enum Status { DRAFT, APPROVED, RECEIVED, CANCELLED }
}
