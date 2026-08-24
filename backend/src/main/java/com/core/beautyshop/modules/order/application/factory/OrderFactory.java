package com.core.beautyshop.modules.order.application.factory;

import com.core.beautyshop.modules.order.application.dto.request.CheckoutRequest;
import com.core.beautyshop.modules.order.domain.Order;
import com.core.beautyshop.modules.order.domain.enums.OrderStatus;
import com.core.beautyshop.modules.order.domain.enums.PaymentStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.UUID;

@Component
public class OrderFactory {

    public Order createOrder(CheckoutRequest request, Long userId) {
        return Order.builder()
                .orderNumber(generateOrderNumber())
                .userId(userId)
                .customerName(request.getCustomerName())
                .customerPhone(request.getCustomerPhone())
                .shippingAddress(request.getShippingAddress())
                .ward(request.getWard())
                .district(request.getDistrict())
                .city(request.getCity())
                .paymentMethod(request.getPaymentMethod())
                .paymentStatus(PaymentStatus.PENDING)
                .status(OrderStatus.PENDING)
                .notes(request.getNotes())
                .shippingFee(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .items(new ArrayList<>())
                .statusHistories(new ArrayList<>())
                .build();
    }

    private String generateOrderNumber() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
