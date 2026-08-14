package com.core.beautyshop.service.payment;

import com.core.beautyshop.dto.response.PaymentInstruction;
import com.core.beautyshop.entities.order.Order;

public interface PaymentStrategy {
    PaymentInstruction processPayment(Order order);
}
