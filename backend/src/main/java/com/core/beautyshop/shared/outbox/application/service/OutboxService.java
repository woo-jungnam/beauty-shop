package com.core.beautyshop.shared.outbox.application.service;

public interface OutboxService {
    void recordEvent(String aggregateType, String aggregateId, String topic, String messageKey, Object payload);
}
