package com.core.beautyshop.modules.order.domain.event;

import com.core.beautyshop.modules.order.domain.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

public class OrderEvents {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderCreatedEvent {
        private Long orderId;
        private String orderNumber;
        private Long userId;
        private String sessionId;
        private BigDecimal totalAmount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderCancelledEvent {
        private Long orderId;
        private String orderNumber;
        private List<OrderItemSummary> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderStatusChangedEvent {
        private Long orderId;
        private String orderNumber;
        private OrderStatus previousStatus;
        private OrderStatus newStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemSummary {
        private Long variantId;
        private Integer quantity;
    }
}
