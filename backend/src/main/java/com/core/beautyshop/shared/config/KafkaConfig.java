package com.core.beautyshop.shared.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
@EnableKafka
public class KafkaConfig {

    // ==========================================
    // TOPIC DEFINITIONS (Auto-created by Spring)
    // ==========================================

    @Bean
    public NewTopic orderCreatedTopic() {
        return TopicBuilder.name(KafkaTopicConstants.ORDER_CREATED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderCancelledTopic() {
        return TopicBuilder.name(KafkaTopicConstants.ORDER_CANCELLED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderStatusChangedTopic() {
        return TopicBuilder.name(KafkaTopicConstants.ORDER_STATUS_CHANGED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic notificationEmailTopic() {
        return TopicBuilder.name(KafkaTopicConstants.NOTIFICATION_EMAIL_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    // ==========================================
    // ERROR HANDLER & DEAD LETTER TOPIC (DLT)
    // ==========================================

    /**
     * Cấu hình ErrorHandler cho Kafka Consumer:
     * - Khi Consumer xử lý tin nhắn gặp lỗi: Retry 3 lần (khoảng cách 1 giây).
     * - Nếu sau 3 lần vẫn lỗi: Tự động chuyển message sang Dead Letter Topic (.DLT)
     *   giúp tránh nghẽn luồng xử lý và không làm mất dữ liệu.
     */
    @Bean
    public CommonErrorHandler errorHandler(KafkaOperations<Object, Object> kafkaOperations) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaOperations);
        
        // 3 retry attempts, 1000ms delay between retries
        FixedBackOff backOff = new FixedBackOff(1000L, 3L);
        
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) -> {
            log.warn("Kafka Consumer retry attempt #{} for topic={}, partition={}, offset={}, error={}",
                    deliveryAttempt, record.topic(), record.partition(), record.offset(), ex.getMessage());
        });

        return errorHandler;
    }

    /**
     * Gán CommonErrorHandler vào Kafka Listener Container Factory
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
            ConsumerFactory<Object, Object> consumerFactory,
            CommonErrorHandler errorHandler) {
        
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
