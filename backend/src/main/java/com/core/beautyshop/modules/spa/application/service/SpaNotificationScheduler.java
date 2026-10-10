package com.core.beautyshop.modules.spa.application.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;

@Component @EnableScheduling @RequiredArgsConstructor @Slf4j
@ConditionalOnProperty(name="app.spa.notifications.enabled", havingValue="true")
public class SpaNotificationScheduler {
    private final JdbcTemplate jdbc;
    private final SpaNotificationWorkflow workflow;
    @Value("${app.jobs.enabled:true}") private boolean jobsEnabled;
    @Value("${app.spa.notifications.reminder-hours:24}") private int hoursAhead;

    @PostConstruct
    void validateConfiguration() {
        if (hoursAhead < 1 || hoursAhead > 168) throw new IllegalStateException("Spa reminder-hours must be between 1 and 168");
    }

    @Scheduled(fixedDelayString="${app.spa.notifications.poll-interval-ms:60000}")
    public void enqueueDueNotifications() {
        enqueueDueNotifications(Instant.now());
    }

    public void enqueueDueNotifications(Instant now) {
        if (!jobsEnabled) return;
        var local = now.atZone(SpaTimeRules.ZONE);
        var horizon = local.plusHours(hoursAhead);
        // Hibernate's UTC JDBC TIME encoding can wrap midnight while appointment_date stays local.
        // Page candidates by date/ID; the locked ORM appointment applies the exact Instant window.
        long cursor = 0;
        while (true) {
            var candidates = SpaJdbcSupport.query(jdbc, """
            SELECT a.id FROM appointments a WHERE a.is_deleted=false AND a.status='CONFIRMED' AND a.checked_in_at IS NULL
              AND a.appointment_date>=? AND a.appointment_date<=? AND a.id>?
              AND NOT EXISTS (SELECT 1 FROM spa_notification_dispatches d WHERE d.appointment_id=a.id
                AND d.notification_kind='REMINDER' AND d.appointment_date=a.appointment_date AND d.start_time=a.start_time)
            ORDER BY a.id LIMIT 200
            """, (rs, row) -> rs.getLong(1), local.toLocalDate(), horizon.toLocalDate(), cursor);
            if (candidates.isEmpty()) break;
            for (Long id : candidates) {
                try { workflow.enqueueReminder(id, now, hoursAhead); }
                catch (Exception exception) { log.warn("Could not enqueue spa reminder for appointment {}", id, exception); }
            }
            cursor = candidates.getLast();
        }
        for (Long id : SpaJdbcSupport.query(jdbc, """
            SELECT i.id FROM spa_follow_up_instructions i JOIN appointments a ON a.id=i.appointment_id
            WHERE a.is_deleted=false AND a.status='COMPLETED' AND i.due_at<=?
              AND NOT EXISTS (SELECT 1 FROM spa_notification_dispatches d WHERE d.instruction_id=i.id AND d.notification_kind='FOLLOW_UP')
            ORDER BY i.due_at,i.id LIMIT 200
            """, (rs, row) -> rs.getLong(1), Timestamp.from(now))) {
            try { workflow.enqueueFollowUp(id, now); }
            catch (Exception exception) { log.warn("Could not enqueue spa follow-up instruction {}", id, exception); }
        }
    }
}
