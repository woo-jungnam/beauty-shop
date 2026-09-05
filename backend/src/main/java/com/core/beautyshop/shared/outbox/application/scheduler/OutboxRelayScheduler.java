package com.core.beautyshop.shared.outbox.application.scheduler;

import com.core.beautyshop.shared.outbox.domain.OutboxMessage;
import com.core.beautyshop.shared.outbox.domain.OutboxMessageRepository;
import com.core.beautyshop.shared.outbox.domain.enums.OutboxStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private final OutboxMessageRepository outboxMessageRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRIES = 5;

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1500}")
    @Transactional
    public void processOutboxMessages() {
        List<OutboxMessage> pendingMessages = outboxMessageRepository.findPendingMessages(PageRequest.of(0, BATCH_SIZE));

        if (pendingMessages.isEmpty()) {
            return;
        }

        log.debug("Tìm thấy {} tin nhắn outbox đang chờ chuyển tiếp tới Kafka", pendingMessages.size());

        for (OutboxMessage message : pendingMessages) {
            try {
                // Gửi payload JSON nguyên vẹn sang Kafka topic
                kafkaTemplate.send(message.getTopic(), message.getMessageKey(), message.getPayload())
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                log.error("Lỗi khi chuyển tiếp OutboxMessage id={} tới topic={}: {}",
                                        message.getId(), message.getTopic(), ex.getMessage());
                                handleMessageFailure(message.getId(), ex.getMessage());
                            } else {
                                log.info("Chuyển tiếp thành công OutboxMessage id={} tới topic={} partition={} offset={}",
                                        message.getId(),
                                        result.getRecordMetadata().topic(),
                                        result.getRecordMetadata().partition(),
                                        result.getRecordMetadata().offset());
                                handleMessageSuccess(message.getId());
                            }
                        });
            } catch (Exception e) {
                log.error("Lỗi khi kích hoạt gửi OutboxMessage id={}: {}", message.getId(), e.getMessage(), e);
                handleMessageFailure(message.getId(), e.getMessage());
            }
        }
    }

    @Transactional
    public void handleMessageSuccess(Long messageId) {
        try {
            outboxMessageRepository.markAsPublished(messageId, OutboxStatus.PUBLISHED, LocalDateTime.now());
        } catch (Exception e) {
            log.error("Lỗi khi cập nhật trạng thái PUBLISHED cho outbox message id={}: {}", messageId, e.getMessage());
        }
    }

    @Transactional
    public void handleMessageFailure(Long messageId, String errorMessage) {
        try {
            outboxMessageRepository.findById(messageId).ifPresent(msg -> {
                if (msg.getRetryCount() + 1 >= MAX_RETRIES) {
                    outboxMessageRepository.markAsFailed(messageId, errorMessage);
                    log.error("NGHIÊM TRỌNG: OutboxMessage id={} đã đạt số lần thử lại tối đa (MAX_RETRIES={}). Đã đánh dấu FAILED! Cần kiểm tra cảnh báo.",
                            messageId, MAX_RETRIES);
                } else {
                    outboxMessageRepository.incrementRetryCount(messageId, errorMessage);
                }
            });
        } catch (Exception e) {
            log.error("Lỗi khi cập nhật trạng thái thất bại cho outbox message id={}: {}", messageId, e.getMessage());
        }
    }
}
