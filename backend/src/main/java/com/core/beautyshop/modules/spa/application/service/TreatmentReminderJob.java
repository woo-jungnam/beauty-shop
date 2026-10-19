package com.core.beautyshop.modules.spa.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.LocalDate;
import java.sql.Date;
import java.util.List;

/** Durable in-app reminders. Delivery through external channels remains separately configured. */
@Component @RequiredArgsConstructor @Slf4j
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class TreatmentReminderJob {
    private final JdbcTemplate jdbc;
    private final TreatmentNotificationService notifications;

    @Scheduled(fixedDelayString = "${app.spa.treatment-reminder-delay-ms:300000}")
    @SchedulerLock(name = "treatmentReminders", lockAtMostFor = "PT4M", lockAtLeastFor = "PT5S")
    public void remind() {
        LocalDate today = LocalDate.now(SpaTimeRules.ZONE);
        List<Reminder> appointments = jdbc.query("""
                SELECT DISTINCT c.id course_id,c.user_id,a.id appointment_id,a.appointment_date,a.start_time
                FROM treatment_courses c
                JOIN treatment_session_items si ON si.course_id=c.id AND si.retired=FALSE
                JOIN appointment_items ai ON ai.id=si.appointment_item_id
                JOIN appointments a ON a.id=ai.appointment_id
                WHERE c.status IN ('PENDING_INTAKE','ACTIVE') AND a.is_deleted=FALSE
                  AND (c.expiry_date IS NULL OR c.expiry_date>CURRENT_TIMESTAMP)
                  AND a.status IN ('PENDING','CONFIRMED')
                  AND a.appointment_date>=? AND a.appointment_date<=?
                """, (rs, i) -> new Reminder(rs.getLong("course_id"), rs.getLong("user_id"),
                "VISIT:" + rs.getLong("appointment_id") + ":" + today,
                "Lịch điều trị sắp đến", "Bạn có lịch ngày " + rs.getDate("appointment_date")
                + " lúc " + rs.getTime("start_time") + ". Mở hồ sơ để xem lịch và hoàn thành biểu mẫu trước buổi."),
                Date.valueOf(today), Date.valueOf(today.plusDays(1)));
        appointments.forEach(this::deliver);

        jdbc.query("""
                SELECT c.id course_id,c.user_id,s.id session_id,s.min_gap_days,
                   (SELECT MAX(p.completed_at) FROM treatment_sessions p
                      WHERE p.course_id=c.id AND p.retired=FALSE AND p.sequence_number<s.sequence_number AND p.status='COMPLETED') previous_completed
                FROM treatment_courses c JOIN treatment_sessions s ON s.course_id=c.id
                WHERE c.status='ACTIVE' AND s.status='PLANNED' AND s.retired=FALSE
                  AND (c.expiry_date IS NULL OR c.expiry_date>CURRENT_TIMESTAMP)
                  AND NOT EXISTS (SELECT 1 FROM treatment_sessions p WHERE p.course_id=c.id
                    AND p.retired=FALSE AND p.sequence_number<s.sequence_number AND p.is_required=TRUE AND p.status NOT IN ('COMPLETED','WAIVED'))
                """, (rs, i) -> {
            var previous = rs.getTimestamp("previous_completed");
            Instant earliest = previous == null ? Instant.EPOCH : previous.toInstant().plusSeconds(rs.getInt("min_gap_days") * 86400L);
            if (!earliest.isAfter(Instant.now()))
                return new Reminder(rs.getLong("course_id"), rs.getLong("user_id"),
                        "DUE:" + rs.getLong("session_id") + ":" + today,
                        "Đến thời gian đặt buổi tiếp theo", "Một buổi trong lộ trình đã đủ điều kiện về thời gian. Mở hồ sơ để chọn lịch còn trống.");
            return null;
        }).stream().filter(java.util.Objects::nonNull).forEach(this::deliver);
    }

    private void deliver(Reminder r) {
        try { notifications.enqueue(r.courseId(), r.userId(), r.key(), "REMINDER", r.title(), r.message()); }
        catch (DataIntegrityViolationException duplicate) { log.debug("Treatment reminder already recorded: {}", r.key()); }
    }

    private record Reminder(Long courseId, Long userId, String key, String title, String message) { }
}
