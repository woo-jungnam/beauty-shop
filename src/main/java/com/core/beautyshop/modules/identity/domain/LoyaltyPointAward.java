package com.core.beautyshop.modules.identity.domain;

import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "loyalty_point_awards",
        uniqueConstraints = @UniqueConstraint(name = "uk_loyalty_award_order", columnNames = "order_id")
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoyaltyPointAward extends Base {

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "points", nullable = false, updatable = false)
    private Integer points;

    @lombok.Setter
    @Column(name = "reversed", nullable = false)
    @Builder.Default
    private boolean reversed = false;
}
