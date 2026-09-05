package com.core.beautyshop.modules.notification.application.consumer;

import com.core.beautyshop.modules.notification.application.service.NotificationService;
import com.core.beautyshop.shared.config.KafkaTopicConstants;
import com.core.beautyshop.shared.event.OrderKafkaMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderNotificationKafkaListener {

    private final NotificationService notificationService;

    @KafkaListener(
            topics = KafkaTopicConstants.ORDER_CREATED_TOPIC,
            groupId = KafkaTopicConstants.NOTIFICATION_GROUP_ID
    )
    public void handleOrderCreated(@Payload OrderKafkaMessage.OrderCreatedKafkaMessage message,
                                   @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                   @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                   @Header(KafkaHeaders.OFFSET) long offset) {
        log.info("Kafka Consumer đã nhận OrderCreatedKafkaMessage từ topic={}, partition={}, offset={}, orderId={}",
                topic, partition, offset, message.getOrderId());

        notificationService.sendOrderConfirmationNotification(
                message.getOrderId(),
                message.getOrderNumber(),
                message.getUserId(),
                message.getTotalAmount()
        );
    }

    @KafkaListener(
            topics = KafkaTopicConstants.ORDER_STATUS_CHANGED_TOPIC,
            groupId = KafkaTopicConstants.NOTIFICATION_GROUP_ID
    )
    public void handleOrderStatusChanged(@Payload OrderKafkaMessage.OrderStatusChangedKafkaMessage message,
                                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                         @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                         @Header(KafkaHeaders.OFFSET) long offset) {
        log.info("Kafka Consumer đã nhận OrderStatusChangedKafkaMessage từ topic={}, partition={}, offset={}, orderId={}, trạng thái {} -> {}",
                topic, partition, offset, message.getOrderId(), message.getPreviousStatus(), message.getNewStatus());

        notificationService.sendOrderStatusUpdateNotification(
                message.getOrderId(),
                message.getOrderNumber(),
                message.getPreviousStatus(),
                message.getNewStatus()
        );
    }

    @KafkaListener(
            topics = KafkaTopicConstants.ORDER_CANCELLED_TOPIC,
            groupId = KafkaTopicConstants.NOTIFICATION_GROUP_ID
    )
    public void handleOrderCancelled(@Payload OrderKafkaMessage.OrderCancelledKafkaMessage message,
                                     @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                     @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                     @Header(KafkaHeaders.OFFSET) long offset) {
        log.info("Kafka Consumer đã nhận OrderCancelledKafkaMessage từ topic={}, partition={}, offset={}, orderId={}",
                topic, partition, offset, message.getOrderId());

        notificationService.sendOrderCancelledNotification(
                message.getOrderId(),
                message.getOrderNumber()
        );
    }

    @DltHandler
    public void handleDltMessage(Object payload,
                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                 @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                 @Header(KafkaHeaders.OFFSET) long offset) {
        log.error("NGHIÊM TRỌNG: Tin nhắn rơi vào DEAD LETTER TOPIC (DLT)! Topic: {}, Partition: {}, Offset: {}, Nội dung: {}",
                topic, partition, offset, payload);
    }
}
