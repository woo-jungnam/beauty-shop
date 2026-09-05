package com.core.beautyshop.modules.payment.application.facade;

import com.core.beautyshop.modules.payment.api.PaymentFacade;
import com.core.beautyshop.modules.payment.api.dto.PaymentInstruction;
import com.core.beautyshop.modules.payment.api.dto.PaymentOrderDto;
import com.core.beautyshop.modules.payment.application.strategy.PaymentStrategy;
import com.core.beautyshop.modules.payment.application.strategy.PaymentStrategyFactory;
import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentFacadeImpl implements PaymentFacade {

    private final PaymentStrategyFactory paymentStrategyFactory;

    @Override
    public PaymentInstruction processPayment(PaymentMethod method, PaymentOrderDto orderDto) {
        PaymentStrategy strategy = paymentStrategyFactory.getStrategy(method);
        return strategy.processPayment(orderDto);
    }
}
