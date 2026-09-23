package com.core.beautyshop.shared.outbox.application.scheduler;
import com.core.beautyshop.shared.outbox.application.service.OutboxClaimService;
import com.core.beautyshop.shared.outbox.domain.OutboxMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

@Component @EnableScheduling @RequiredArgsConstructor @Slf4j
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelayScheduler {
    private final OutboxClaimService claims;
    private final KafkaTemplate<String, String> kafka;

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:250}")
    public void processOutboxMessages() {
        Map<OutboxMessage, CompletableFuture<SendResult<String, String>>> pending = new LinkedHashMap<>();
        for (OutboxMessage message : claims.claimBatch()) {
            ProducerRecord<String, String> record = new ProducerRecord<>(message.getTopic(), message.getMessageKey(), message.getPayload());
            record.headers().add("event-id", ("outbox-" + message.getId()).getBytes(StandardCharsets.UTF_8));
            try { pending.put(message, kafka.send(record)); }
            catch (Exception exception) { claims.failure(message, exception); }
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        for (var entry : pending.entrySet()) {
            try {
                entry.getValue().get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                claims.success(entry.getKey());
            } catch (Exception exception) {
                if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
                claims.failure(entry.getKey(), exception);
                log.warn("Outbox delivery failed for event {}", entry.getKey().getId());
            }
        }
    }
}
