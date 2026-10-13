package com.core.beautyshop.modules.identity.domain.enums;

import lombok.Getter;

@Getter
public enum MembershipTier {
    MEMBER(0, 0),
    SILVER(5, 1000),
    GOLD(10, 5000),
    PLATINUM(15, 10000);

    private final int discountPercentage;
    private final int requiredPoints;

    MembershipTier(int discountPercentage, int requiredPoints) {
        this.discountPercentage = discountPercentage;
        this.requiredPoints = requiredPoints;
    }
}
