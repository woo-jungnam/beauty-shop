package com.core.beautyshop.modules.order.api.dto;

import com.core.beautyshop.modules.payment.api.dto.PaymentInstruction;
import com.core.beautyshop.modules.order.domain.enums.OrderStatus;
import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import com.core.beautyshop.modules.order.domain.enums.PaymentStatus;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;

@Value
@Builder
public class SpaPackageOrderResult {
    Long orderId;
    String orderNumber;
    Long servicePackageId;
    BigDecimal totalAmount;
    OrderStatus status;
    PaymentMethod paymentMethod;
    PaymentStatus paymentStatus;
    Instant paymentDeadline;
    Instant createdAt;
    PaymentInstruction paymentInstruction;
}
