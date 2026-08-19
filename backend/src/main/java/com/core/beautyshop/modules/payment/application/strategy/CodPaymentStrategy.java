package com.core.beautyshop.modules.payment.application.strategy;

import com.core.beautyshop.modules.payment.api.dto.PaymentOrderDto;
import com.core.beautyshop.modules.payment.application.dto.response.PaymentInstruction;
import org.springframework.stereotype.Service;

@Service("codPaymentStrategy")
public class CodPaymentStrategy implements PaymentStrategy {

    @Override
    public PaymentInstruction processPayment(PaymentOrderDto order) {
        return PaymentInstruction.builder()
                .method("COD")
                .instructionMessage("Vui lòng thanh toán số tiền " + order.getTotalAmount() + " VNĐ khi nhận hàng.")
                .build();
    }
}
