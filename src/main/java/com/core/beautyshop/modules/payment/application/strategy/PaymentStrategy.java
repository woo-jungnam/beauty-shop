package com.core.beautyshop.modules.payment.application.strategy;

import com.core.beautyshop.modules.payment.api.dto.PaymentInstruction;
import com.core.beautyshop.modules.payment.api.dto.PaymentOrderDto;

public interface PaymentStrategy {
    PaymentInstruction processPayment(PaymentOrderDto order);
}
