package com.core.beautyshop.modules.inventory.domain;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "stock_allocations", uniqueConstraints = @UniqueConstraint(columnNames = {"order_number", "stock_id"}))
@Getter @Setter @NoArgsConstructor
public class StockAllocation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "order_number", nullable = false, length = 50)
    private String orderNumber;
    @Column(name = "stock_id", nullable = false)
    private Long stockId;
    @Column(name = "variant_id", nullable = false)
    private Long variantId;
    @Column(nullable = false)
    private int quantity;
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Status status = Status.RESERVED;
    public enum Status { RESERVED, RELEASED, DEDUCTED, QUARANTINED }
}
