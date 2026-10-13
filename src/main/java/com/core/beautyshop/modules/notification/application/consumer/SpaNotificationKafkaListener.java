package com.core.beautyshop.modules.notification.application.consumer;

import com.core.beautyshop.modules.identity.api.IdentityFacade;
import com.core.beautyshop.modules.notification.application.dto.EmailMessageDto;
import com.core.beautyshop.modules.notification.application.service.NotificationInbox;
import com.core.beautyshop.modules.notification.application.service.NotificationService;
import com.core.beautyshop.modules.spa.api.SpaNotificationFacade;
import com.core.beautyshop.shared.event.SpaNotificationMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name="app.spa.notifications.enabled", havingValue="true")
public class SpaNotificationKafkaListener {
    private final SpaNotificationFacade spa;
    private final IdentityFacade identity;
    private final NotificationService notifications;
    private final NotificationInbox inbox;

    @KafkaListener(topics="spa.notification", groupId="beautyshop-spa-notification")
    public void deliver(@Payload SpaNotificationMessage message,
                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                        @Header(KafkaHeaders.OFFSET) long offset,
                        @Header(value="event-id", required=false) String eventId) {
        inbox.process(eventId == null ? topic + ":" + partition + ":" + offset : eventId, () -> {
            var delivery = spa.currentDelivery(message, Instant.now()).orElse(null);
            if (delivery == null) return;
            var customer = identity.findUserSummaryById(delivery.customerId()).orElse(null);
            if (customer == null || customer.getEmail() == null || customer.getEmail().isBlank()) return;
            notifications.sendEmail(EmailMessageDto.builder().recipientEmail(customer.getEmail())
                    .recipientName(customer.getFullName() == null ? customer.getUsername() : customer.getFullName())
                    .subject(delivery.subject()).templateCode(delivery.templateCode()).parameters(Map.of("body", delivery.body())).build());
        });
    }
}
