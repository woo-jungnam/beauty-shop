package com.core.beautyshop.modules.order.application.service;
import com.core.beautyshop.modules.order.domain.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.PageRequest;

@Component @RequiredArgsConstructor @Slf4j
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class OrderExpirationJob {
    private final OrderRepository orders;
    private final OrderExpirationService expiration;
    @Scheduled(fixedDelayString = "${app.order.expiration-poll-ms:30000}")
    @SchedulerLock(name = "order_expiration", lockAtMostFor = "5m")
    public void expireOrders() {
        for (Long id : orders.findExpiredPaymentIds(java.time.Instant.now(), PageRequest.of(0, 100))) {
            try { expiration.expire(id); }
            catch (Exception exception) { log.error("Could not expire order {}", id, exception); }
        }
    }
}
