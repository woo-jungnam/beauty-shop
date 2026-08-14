package com.core.beautyshop.mapper;

import com.core.beautyshop.dto.response.OrderItemResponse;
import com.core.beautyshop.dto.response.OrderResponse;
import com.core.beautyshop.entities.order.Order;
import com.core.beautyshop.entities.order.OrderItem;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class OrderMapper {

    public OrderResponse toOrderResponse(Order order) {
        if (order == null) {
            return null;
        }
        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .customerName(order.getCustomerName())
                .customerPhone(order.getCustomerPhone())
                .shippingAddress(order.getShippingAddress() + ", " + order.getWard() + ", " + order.getDistrict() + ", " + order.getCity())
                .subTotal(order.getSubTotal())
                .shippingFee(order.getShippingFee())
                .discountAmount(order.getDiscountAmount())
                .totalAmount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                .items(order.getItems() != null ? 
                        order.getItems().stream().map(this::toOrderItemResponse).collect(Collectors.toList()) 
                        : List.of())
                .build();
    }

    public OrderItemResponse toOrderItemResponse(OrderItem item) {
        if (item == null) {
            return null;
        }
        return OrderItemResponse.builder()
                .id(item.getId())
                .variantId(item.getProductVariant().getId())
                .variantName(item.getProductVariant().getVariantName())
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .discount(item.getDiscount())
                .build();
    }
}
