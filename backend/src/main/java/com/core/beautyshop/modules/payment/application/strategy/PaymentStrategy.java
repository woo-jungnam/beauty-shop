package com.core.beautyshop.modules.payment.application.strategy;

import com.core.beautyshop.modules.payment.api.dto.PaymentOrderDto;
import com.core.beautyshop.modules.payment.application.dto.response.PaymentInstruction;

public interface PaymentStrategy {
    PaymentInstruction processPayment(PaymentOrderDto order);
}
