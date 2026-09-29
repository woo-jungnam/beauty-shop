package com.core.beautyshop.modules.promotion.domain;

import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "voucher_usages")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VoucherUsage extends Base {
    @Column(name = "voucher_id", nullable = false)
    private Long voucherId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;
    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount;
    @Column(name = "used_at", nullable = false)
    private Instant usedAt;
}
