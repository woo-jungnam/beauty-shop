package com.core.beautyshop.modules.promotion.domain;

import com.core.beautyshop.modules.promotion.domain.enums.DiscountType;
import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "vouchers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Voucher extends Base {
    @Column(nullable = false, unique = true, length = 50)
    private String code;
    @Column(nullable = false, length = 150)
    private String name;
    @Column(length = 500)
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;
    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;
    @Column(name = "max_discount_amount", precision = 12, scale = 2)
    private BigDecimal maxDiscountAmount;
    @Column(name = "min_order_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal minOrderAmount = BigDecimal.ZERO;
    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;
    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;
    @Column(name = "usage_limit")
    private Integer usageLimit;
    @Column(name = "per_user_limit", nullable = false)
    @Builder.Default
    private Integer perUserLimit = 1;
    @Column(name = "used_count", nullable = false)
    @Builder.Default
    private Integer usedCount = 0;
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
