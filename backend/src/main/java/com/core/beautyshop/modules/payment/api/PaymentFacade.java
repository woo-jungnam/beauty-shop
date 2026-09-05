package com.core.beautyshop.modules.payment.api;

import com.core.beautyshop.modules.payment.api.dto.PaymentInstruction;
import com.core.beautyshop.modules.payment.api.dto.PaymentOrderDto;
import com.core.beautyshop.shared.domain.enums.PaymentMethod;

public interface PaymentFacade {
    PaymentInstruction processPayment(PaymentMethod method, PaymentOrderDto orderDto);
}
