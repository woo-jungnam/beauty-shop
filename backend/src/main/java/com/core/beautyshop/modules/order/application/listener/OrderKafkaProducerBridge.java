package com.core.beautyshop.modules.order.application.listener;

import com.core.beautyshop.modules.order.api.event.OrderEvents;
import com.core.beautyshop.shared.config.KafkaTopicConstants;
import com.core.beautyshop.shared.event.OrderKafkaMessage;
import com.core.beautyshop.shared.outbox.application.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderKafkaProducerBridge {

    private final OutboxService outboxService;

    @EventListener
    public void onOrderCreated(OrderEvents.OrderCreatedEvent event) {
        log.info("Ghi nhận OrderCreatedEvent vào Outbox cho orderId={}", event.getOrderId());

        OrderKafkaMessage.OrderCreatedKafkaMessage message = OrderKafkaMessage.OrderCreatedKafkaMessage.builder()
                .orderId(event.getOrderId())
                .orderNumber(event.getOrderNumber())
                .userId(event.getUserId())
                .sessionId(event.getSessionId())
                .totalAmount(event.getTotalAmount())
                .occurredAt(LocalDateTime.now())
                .build();

        String messageKey = String.valueOf(event.getOrderId());

        outboxService.recordEvent(
                "ORDER",
                String.valueOf(event.getOrderId()),
                KafkaTopicConstants.ORDER_CREATED_TOPIC,
                messageKey,
                message
        );
    }

    @EventListener
    public void onOrderCancelled(OrderEvents.OrderCancelledEvent event) {
        log.info("Ghi nhận OrderCancelledEvent vào Outbox cho orderId={}", event.getOrderId());

        List<OrderKafkaMessage.OrderItemSummaryMessage> itemMessages = event.getItems() != null
                ? event.getItems().stream()
                .map(item -> OrderKafkaMessage.OrderItemSummaryMessage.builder()
                        .variantId(item.getVariantId())
                        .quantity(item.getQuantity())
                        .build())
                .collect(Collectors.toList())
                : Collections.emptyList();

        OrderKafkaMessage.OrderCancelledKafkaMessage message = OrderKafkaMessage.OrderCancelledKafkaMessage.builder()
                .orderId(event.getOrderId())
                .orderNumber(event.getOrderNumber())
                .items(itemMessages)
                .occurredAt(LocalDateTime.now())
                .build();

        String messageKey = String.valueOf(event.getOrderId());

        outboxService.recordEvent(
                "ORDER",
                String.valueOf(event.getOrderId()),
                KafkaTopicConstants.ORDER_CANCELLED_TOPIC,
                messageKey,
                message
        );
    }

    @EventListener
    public void onOrderStatusChanged(OrderEvents.OrderStatusChangedEvent event) {
        log.info("Ghi nhận OrderStatusChangedEvent vào Outbox cho orderId={}, trạng thái {} -> {}",
                event.getOrderId(), event.getPreviousStatus(), event.getNewStatus());

        OrderKafkaMessage.OrderStatusChangedKafkaMessage message = OrderKafkaMessage.OrderStatusChangedKafkaMessage.builder()
                .orderId(event.getOrderId())
                .orderNumber(event.getOrderNumber())
                .previousStatus(event.getPreviousStatus() != null ? event.getPreviousStatus().name() : null)
                .newStatus(event.getNewStatus() != null ? event.getNewStatus().name() : null)
                .occurredAt(LocalDateTime.now())
                .build();

        String messageKey = String.valueOf(event.getOrderId());

        outboxService.recordEvent(
                "ORDER",
                String.valueOf(event.getOrderId()),
                KafkaTopicConstants.ORDER_STATUS_CHANGED_TOPIC,
                messageKey,
                message
        );
    }
}
