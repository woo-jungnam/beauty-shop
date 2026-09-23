package com.core.beautyshop.modules.order.api.dto;

import com.core.beautyshop.modules.payment.api.dto.PaymentInstruction;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class SpaPackageOrderResult {
    Long orderId;
    String orderNumber;
    Long servicePackageId;
    BigDecimal totalAmount;
    PaymentInstruction paymentInstruction;
}
