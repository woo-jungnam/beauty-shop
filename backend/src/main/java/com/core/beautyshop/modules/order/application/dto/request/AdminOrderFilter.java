package com.core.beautyshop.modules.order.application.dto.request;

import com.core.beautyshop.modules.order.domain.enums.*;
import java.time.Instant;

public record AdminOrderFilter(
        String keyword,
        OrderStatus status,
        PaymentStatus paymentStatus,
        Instant from,
        Instant to,
        String orderType,
        Boolean pendingReturn,
        String codReconciliation,
        Long shipperId,
        Boolean carrierOnly
) {
    public AdminOrderFilter(String keyword, OrderStatus status, PaymentStatus paymentStatus,
                            Instant from, Instant to, String orderType) {
        this(keyword, status, paymentStatus, from, to, orderType, null, null, null, null);
    }

    public AdminOrderFilter(String keyword, OrderStatus status, PaymentStatus paymentStatus,
                            Instant from, Instant to, String orderType, Boolean pendingReturn) {
        this(keyword, status, paymentStatus, from, to, orderType, pendingReturn, null, null, null);
    }
}
