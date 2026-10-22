package com.core.beautyshop.modules.spa.api;

import com.core.beautyshop.shared.event.SpaNotificationMessage;

import java.time.Instant;
import java.util.Optional;

public interface SpaNotificationFacade {
    Optional<Delivery> currentDelivery(SpaNotificationMessage message, Instant now);
    record Delivery(Long customerId, String subject, String body, String templateCode) {}
}
