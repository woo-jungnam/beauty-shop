package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.spa.application.service.impl.AppointmentServiceImpl;
import com.core.beautyshop.modules.spa.domain.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.PageRequest;
import java.time.Instant;

@Component @RequiredArgsConstructor @Slf4j
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="app.jobs.enabled", havingValue="true", matchIfMissing=true)
public class PendingAppointmentExpirationJob {
    private final AppointmentRepository appointments;
    private final AppointmentServiceImpl service;
    @Scheduled(fixedDelayString = "${spa.pending-expiration.interval-ms:60000}")
    public void expire() {
        long cursor = 0; Instant now = Instant.now();
        while (true) {
            var ids = appointments.findExpiredPendingIds(cursor, now, PageRequest.of(0, 100));
            if (ids.isEmpty()) return;
            for (Long id : ids) {
                try { service.expirePending(id); }
                catch (RuntimeException e) { log.warn("Cannot expire pending appointment {}: {}", id, e.getClass().getSimpleName()); }
                cursor = id;
            }
        }
    }
}
