package com.core.beautyshop.modules.payment.application.strategy;

import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import com.core.beautyshop.shared.exception.BusinessException;
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
            throw new BusinessException("Phương thức thanh toán là bắt buộc");
        }
        
        switch (method) {
            case COD:
                return codPaymentStrategy;
            case BANK:
                return bankPaymentStrategy;
            default:
                throw new BusinessException("Phương thức thanh toán không được hỗ trợ: " + method);
        }
    }
}
