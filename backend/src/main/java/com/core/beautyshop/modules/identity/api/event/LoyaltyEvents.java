package com.core.beautyshop.modules.identity.api.event;
import java.math.BigDecimal;

public final class LoyaltyEvents {
    private LoyaltyEvents(){}
    public record EligibleSpendChanged(Long orderId,Long userId,BigDecimal eligibleAmount){}
}
