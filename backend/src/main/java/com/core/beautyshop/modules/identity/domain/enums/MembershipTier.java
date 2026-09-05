package com.core.beautyshop.modules.identity.domain.enums;

import lombok.Getter;

@Getter
public enum MembershipTier {
    MEMBER(0, 0),        // 0% discount, 0 points required
    SILVER(5, 1000),     // 5% discount, 1000 points required
    GOLD(10, 5000),      // 10% discount, 5000 points required
    PLATINUM(15, 10000); // 15% discount, 10000 points required

    private final int discountPercentage;
    private final int requiredPoints;

    MembershipTier(int discountPercentage, int requiredPoints) {
        this.discountPercentage = discountPercentage;
        this.requiredPoints = requiredPoints;
    }
}
