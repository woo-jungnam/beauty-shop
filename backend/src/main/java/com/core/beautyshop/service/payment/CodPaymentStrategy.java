package com.core.beautyshop.service.payment;

import com.core.beautyshop.dto.response.PaymentInstruction;
import com.core.beautyshop.entities.order.Order;
import org.springframework.stereotype.Service;

@Service("codPaymentStrategy")
public class CodPaymentStrategy implements PaymentStrategy {

    @Override
    public PaymentInstruction processPayment(Order order) {
        return PaymentInstruction.builder()
                .method("COD")
                .instructionMessage("Vui lòng thanh toán số tiền " + order.getTotalAmount() + " VNĐ khi nhận hàng.")
                .build();
    }
}
