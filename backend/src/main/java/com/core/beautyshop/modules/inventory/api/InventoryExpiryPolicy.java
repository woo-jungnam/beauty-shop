package com.core.beautyshop.modules.inventory.api;

import com.core.beautyshop.shared.time.BusinessTime;
import java.time.LocalDate;
import java.util.Objects;

public final class InventoryExpiryPolicy {
    public static final int MIN_REMAINING_DAYS = 30;
    public static final int MAX_CLEARANCE_DAYS = 90;

    private InventoryExpiryPolicy() {}

    public static LocalDate saleCutoff(LocalDate businessDate) {
        return Objects.requireNonNull(businessDate, "Business date is required").plusDays(MIN_REMAINING_DAYS);
    }

    public static LocalDate saleCutoff() { return saleCutoff(BusinessTime.today()); }

    public static boolean isSellable(LocalDate expiry, LocalDate businessDate) {
        return expiry == null || expiry.isAfter(saleCutoff(businessDate));
    }
}
