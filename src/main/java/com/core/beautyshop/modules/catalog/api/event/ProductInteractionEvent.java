package com.core.beautyshop.modules.catalog.api.event;

public record ProductInteractionEvent(Long userId, String sessionId, Long productId, String actionType, String sourceKey) {}
