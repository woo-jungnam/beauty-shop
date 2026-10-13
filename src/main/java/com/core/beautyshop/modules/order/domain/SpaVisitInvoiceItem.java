package com.core.beautyshop.modules.order.domain;

import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

/** A service invoice snapshot is separate from physical order items and stock allocations. */
@Entity
@Table(name = "spa_visit_invoice_items")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SpaVisitInvoiceItem extends Base {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    @Column(name = "appointment_item_id", nullable = false, unique = true)
    private Long appointmentItemId;
    @Column(name = "service_id", nullable = false)
    private Long serviceId;
    @Column(name = "service_name", nullable = false, length = 255)
    private String serviceName;
    @Column(name = "quantity", nullable = false)
    private Integer quantity;
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;
}
