package com.core.beautyshop.shared.outbox.application.service;

import com.core.beautyshop.shared.outbox.domain.OutboxMessage;
import com.core.beautyshop.shared.outbox.domain.OutboxMessageRepository;
import com.core.beautyshop.shared.outbox.domain.enums.OutboxStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxServiceImpl implements OutboxService {

    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordEvent(String aggregateType, String aggregateId, String topic, String messageKey, Object payload) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);

            OutboxMessage message = OutboxMessage.builder()
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .topic(topic)
                    .messageKey(messageKey)
                    .payload(payloadJson)
                    .status(OutboxStatus.PENDING)
                    .retryCount(0)
                    .build();

            outboxMessageRepository.save(message);
            log.debug("Đã ghi nhận sự kiện Outbox cho aggregate={}:{}, topic={}", aggregateType, aggregateId, topic);
        } catch (Exception e) {
            log.error("Lỗi khi tuần tự hóa và ghi nhận sự kiện outbox cho aggregate={}: {}: {}",
                    aggregateType, aggregateId, e.getMessage(), e);
            throw new RuntimeException("Lỗi ghi nhận sự kiện outbox", e);
        }
    }
}
