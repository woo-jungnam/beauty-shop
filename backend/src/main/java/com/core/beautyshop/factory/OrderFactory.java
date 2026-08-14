package com.core.beautyshop.factory;

import com.core.beautyshop.dto.request.CheckoutRequest;
import com.core.beautyshop.entities.order.Order;
import com.core.beautyshop.entities.order.enums.OrderStatus;
import com.core.beautyshop.entities.order.enums.PaymentStatus;
import com.core.beautyshop.entities.user.User;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.UUID;

@Component
public class OrderFactory {

    public Order createOrder(CheckoutRequest request, User user) {
        return Order.builder()
                .orderNumber(generateOrderNumber())
                .user(user)
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
                .items(new ArrayList<>())
                .statusHistories(new ArrayList<>())
                .build();
    }

    private String generateOrderNumber() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
