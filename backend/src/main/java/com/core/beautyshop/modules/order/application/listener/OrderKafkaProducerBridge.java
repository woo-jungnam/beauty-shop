package com.core.beautyshop.modules.order.application.listener;

import com.core.beautyshop.modules.order.domain.event.OrderEvents;
import com.core.beautyshop.shared.config.KafkaTopicConstants;
import com.core.beautyshop.shared.event.OrderKafkaMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderKafkaProducerBridge {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderEvents.OrderCreatedEvent event) {
        log.info("DB Transaction committed. Bridging OrderCreatedEvent to Kafka for orderId={}", event.getOrderId());

        OrderKafkaMessage.OrderCreatedKafkaMessage message = OrderKafkaMessage.OrderCreatedKafkaMessage.builder()
                .orderId(event.getOrderId())
                .orderNumber(event.getOrderNumber())
                .userId(event.getUserId())
                .sessionId(event.getSessionId())
                .totalAmount(event.getTotalAmount())
                .occurredAt(LocalDateTime.now())
                .build();

        String messageKey = String.valueOf(event.getOrderId());

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                KafkaTopicConstants.ORDER_CREATED_TOPIC,
                messageKey,
                message
        );

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to send OrderCreatedKafkaMessage to Kafka topic={} for orderId={}: {}",
                        KafkaTopicConstants.ORDER_CREATED_TOPIC, event.getOrderId(), ex.getMessage(), ex);
            } else {
                log.info("Successfully sent OrderCreatedKafkaMessage to Kafka topic={} partition={} offset={}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCancelled(OrderEvents.OrderCancelledEvent event) {
        log.info("DB Transaction committed. Bridging OrderCancelledEvent to Kafka for orderId={}", event.getOrderId());

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

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                KafkaTopicConstants.ORDER_CANCELLED_TOPIC,
                messageKey,
                message
        );

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to send OrderCancelledKafkaMessage to Kafka topic={} for orderId={}: {}",
                        KafkaTopicConstants.ORDER_CANCELLED_TOPIC, event.getOrderId(), ex.getMessage(), ex);
            } else {
                log.info("Successfully sent OrderCancelledKafkaMessage to Kafka topic={} partition={} offset={}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderEvents.OrderStatusChangedEvent event) {
        log.info("DB Transaction committed. Bridging OrderStatusChangedEvent to Kafka for orderId={}, status {} -> {}",
                event.getOrderId(), event.getPreviousStatus(), event.getNewStatus());

        OrderKafkaMessage.OrderStatusChangedKafkaMessage message = OrderKafkaMessage.OrderStatusChangedKafkaMessage.builder()
                .orderId(event.getOrderId())
                .orderNumber(event.getOrderNumber())
                .previousStatus(event.getPreviousStatus() != null ? event.getPreviousStatus().name() : null)
                .newStatus(event.getNewStatus() != null ? event.getNewStatus().name() : null)
                .occurredAt(LocalDateTime.now())
                .build();

        String messageKey = String.valueOf(event.getOrderId());

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                KafkaTopicConstants.ORDER_STATUS_CHANGED_TOPIC,
                messageKey,
                message
        );

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to send OrderStatusChangedKafkaMessage to Kafka topic={} for orderId={}: {}",
                        KafkaTopicConstants.ORDER_STATUS_CHANGED_TOPIC, event.getOrderId(), ex.getMessage(), ex);
            } else {
                log.info("Successfully sent OrderStatusChangedKafkaMessage to Kafka topic={} partition={} offset={}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
