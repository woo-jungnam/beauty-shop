package com.core.beautyshop.modules.payment.api;

import com.core.beautyshop.modules.payment.api.dto.PaymentInstruction;
import com.core.beautyshop.modules.payment.api.dto.PaymentOrderDto;
import com.core.beautyshop.shared.domain.enums.PaymentMethod;

public interface PaymentFacade {
    void recordCodCollection(String orderNumber, java.math.BigDecimal amount);
    boolean recordCashCollection(String orderNumber, java.math.BigDecimal amount, String receiptKey, Long receivedBy);
    PaymentInstruction processPayment(PaymentMethod method, PaymentOrderDto orderDto);
}
