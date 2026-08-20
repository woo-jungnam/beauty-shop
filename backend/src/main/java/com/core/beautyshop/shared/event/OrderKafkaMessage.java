package com.core.beautyshop.shared.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class OrderKafkaMessage {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderCreatedKafkaMessage implements Serializable {
        private Long orderId;
        private String orderNumber;
        private Long userId;
        private String sessionId;
        private BigDecimal totalAmount;
        private LocalDateTime occurredAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderCancelledKafkaMessage implements Serializable {
        private Long orderId;
        private String orderNumber;
        private List<OrderItemSummaryMessage> items;
        private LocalDateTime occurredAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderStatusChangedKafkaMessage implements Serializable {
        private Long orderId;
        private String orderNumber;
        private String previousStatus;
        private String newStatus;
        private LocalDateTime occurredAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemSummaryMessage implements Serializable {
        private Long variantId;
        private Integer quantity;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationEmailKafkaMessage implements Serializable {
        private String recipientEmail;
        private String subject;
        private String template;
        private Long orderId;
        private String orderNumber;
        private BigDecimal totalAmount;
        private LocalDateTime sentAt;
    }
}
