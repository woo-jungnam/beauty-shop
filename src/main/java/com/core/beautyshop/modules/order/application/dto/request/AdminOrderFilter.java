package com.core.beautyshop.modules.order.application.dto.request;

import com.core.beautyshop.modules.order.domain.enums.*;
import java.time.Instant;

public record AdminOrderFilter(String keyword, OrderStatus status, PaymentStatus paymentStatus,
                               Instant from, Instant to) { }
