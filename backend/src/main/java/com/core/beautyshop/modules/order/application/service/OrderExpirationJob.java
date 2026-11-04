package com.core.beautyshop.modules.order.application.service;

import com.core.beautyshop.modules.order.domain.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component @RequiredArgsConstructor @Slf4j
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class OrderExpirationJob {
    private final OrderRepository orders;
    private final OrderExpirationService expiration;

    @Scheduled(fixedDelayString = "${app.order.expiration-poll-ms:30000}")
    @SchedulerLock(name = "order_expiration", lockAtMostFor = "5m")
    public void expireOrders() {
        Instant cutoff = Instant.now();
        long cursor = 0;
        while (true) {
            var ids = orders.findExpiredPaymentIdsAfter(cutoff, cursor, PageRequest.of(0, 100));
            if (ids.isEmpty()) return;
            for (Long id : ids) {
                try { expiration.expire(id); }
                catch (Exception exception) { log.error("Could not expire order {}", id, exception); }
            }
            cursor = ids.getLast();
        }
    }
}
