package com.core.beautyshop.modules.identity.api.event;
import java.math.BigDecimal;

/** Target eligible performed spend, not an increment; publishers may retry safely. */
public final class LoyaltyEvents {
    private LoyaltyEvents(){}
    public record EligibleSpendChanged(Long orderId,Long userId,BigDecimal eligibleAmount){}
}
