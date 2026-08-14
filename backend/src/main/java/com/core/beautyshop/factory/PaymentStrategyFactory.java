package com.core.beautyshop.factory;

import com.core.beautyshop.entities.order.enums.PaymentMethod;
import com.core.beautyshop.exception.BusinessException;
import com.core.beautyshop.service.payment.PaymentStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class PaymentStrategyFactory {

    private final PaymentStrategy codPaymentStrategy;
    private final PaymentStrategy bankPaymentStrategy;

    @Autowired
    public PaymentStrategyFactory(
            @Qualifier("codPaymentStrategy") PaymentStrategy codPaymentStrategy,
            @Qualifier("bankPaymentStrategy") PaymentStrategy bankPaymentStrategy) {
        this.codPaymentStrategy = codPaymentStrategy;
        this.bankPaymentStrategy = bankPaymentStrategy;
    }

    public PaymentStrategy getStrategy(PaymentMethod method) {
        if (method == null) {
            throw new BusinessException("Payment method is required");
        }
        
        switch (method) {
            case COD:
                return codPaymentStrategy;
            case BANK:
                return bankPaymentStrategy;
            default:
                throw new BusinessException("Unsupported payment method: " + method);
        }
    }
}
