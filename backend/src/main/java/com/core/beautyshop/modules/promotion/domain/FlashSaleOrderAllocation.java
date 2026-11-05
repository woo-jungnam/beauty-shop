package com.core.beautyshop.modules.promotion.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "flash_sale_order_allocations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlashSaleOrderAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flash_sale_item_id", nullable = false)
    private Long flashSaleItemId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "allocated_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant allocatedAt = Instant.now();

    @Column(name = "released_at")
    private Instant releasedAt;
}
