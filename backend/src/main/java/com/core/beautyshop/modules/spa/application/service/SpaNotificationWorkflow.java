package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.spa.api.SpaNotificationFacade;
import com.core.beautyshop.modules.spa.domain.Appointment;
import com.core.beautyshop.modules.spa.domain.AppointmentItem;
import com.core.beautyshop.modules.spa.domain.AppointmentRepository;
import com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus;
import com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus;
import com.core.beautyshop.shared.event.SpaNotificationMessage;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.shared.outbox.application.service.OutboxService;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import io.swagger.v3.oas.annotations.media.Schema;

@Service
@RequiredArgsConstructor
public class SpaNotificationWorkflow implements SpaNotificationFacade {
    public static final String TOPIC = "spa.notification";
    private final JdbcTemplate jdbc;
    private final AppointmentRepository appointments;
    private final SpaAccessService access;
    private final OutboxService outbox;

    @Transactional
    public Instruction addInstruction(Long appointmentId, Long itemId, InstructionCommand command) {
        Appointment appointment = load(appointmentId, true);
        AppointmentItem item = appointment.getItems().stream().filter(found -> Objects.equals(found.getId(), itemId)
                && !Boolean.TRUE.equals(found.getIsDeleted())).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Appointment item not found"));
        access.requireCanPerformItem(item);
        if (item.getExecutionStatus() != AppointmentItemExecutionStatus.PERFORMED
                && item.getExecutionStatus() != AppointmentItemExecutionStatus.LEGACY_FINALIZED) {
            throw new BusinessException("Follow-up instructions require a performed service item");
        }
        Instant now = Instant.now();
        if (command == null || command.content() == null || command.content().isBlank() || command.content().length() > 4000
                || command.dueAt() == null || command.dueAt().isBefore(now.minus(1, ChronoUnit.MINUTES))
                || command.dueAt().isAfter(now.plus(365, ChronoUnit.DAYS))) {
            throw new BusinessException("Staff instructions require 1 to 4000 characters and a delivery date within the next year");
        }
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        Long actor = SecurityUtils.getCurrentUserId();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO spa_follow_up_instructions(appointment_id,appointment_item_id,content,due_at,author_id,created_at) VALUES (?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, appointmentId); statement.setLong(2, itemId); statement.setString(3, command.content().trim());
            SpaJdbcSupport.bind(statement, 4, command.dueAt()); statement.setLong(5, actor); SpaJdbcSupport.bind(statement, 6, now);
            return statement;
        }, key);
        return instruction(Objects.requireNonNull(key.getKey()).longValue());
    }

    @Transactional(readOnly = true)
    public List<Instruction> instructions(Long appointmentId) {
        access.requireCanViewAppointment(load(appointmentId, false));
        return jdbc.query("SELECT * FROM spa_follow_up_instructions WHERE appointment_id=? ORDER BY id DESC", this::instructionRow, appointmentId);
    }

    @Transactional
    public boolean enqueueReminder(Long appointmentId, Instant now, int hoursAhead) {
        Appointment appointment = load(appointmentId, true);
        Instant scheduled = scheduledAt(appointment);
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED || appointment.getCheckedInAt() != null
                || !scheduled.isAfter(now) || scheduled.isAfter(now.plus(hoursAhead, ChronoUnit.HOURS))) return false;
        String key = "REMINDER:" + appointmentId + ":" + appointment.getAppointmentDate() + ":" + appointment.getStartTime();
        if (dispatched(key)) return false;
        recordDispatch(key, appointment, SpaNotificationMessage.Kind.REMINDER, null, now);
        outbox.recordEvent("APPOINTMENT", appointmentId.toString(), TOPIC, appointmentId.toString(),
                new SpaNotificationMessage(appointmentId, SpaNotificationMessage.Kind.REMINDER, appointment.getAppointmentDate(), appointment.getStartTime(), null));
        return true;
    }

    @Transactional
    public boolean enqueueFollowUp(Long instructionId, Instant now) {
        Instruction instruction = instruction(instructionId);
        Appointment appointment = load(instruction.appointmentId(), true);
        if (appointment.getStatus() != AppointmentStatus.COMPLETED || instruction.dueAt().isAfter(now)) return false;
        String key = "FOLLOW_UP:" + instructionId;
        if (dispatched(key)) return false;
        recordDispatch(key, appointment, SpaNotificationMessage.Kind.FOLLOW_UP, instructionId, now);
        outbox.recordEvent("APPOINTMENT", appointment.getId().toString(), TOPIC, appointment.getId().toString(),
                new SpaNotificationMessage(appointment.getId(), SpaNotificationMessage.Kind.FOLLOW_UP, appointment.getAppointmentDate(), appointment.getStartTime(), instructionId));
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Delivery> currentDelivery(SpaNotificationMessage message, Instant now) {
        Optional<Appointment> found = appointments.findByIdWithItems(message.appointmentId())
                .filter(appointment -> !Boolean.TRUE.equals(appointment.getIsDeleted()));
        if (found.isEmpty()) return Optional.empty();
        Appointment appointment = found.get();
        if (message.kind() == SpaNotificationMessage.Kind.REMINDER) {
            if (appointment.getStatus() != AppointmentStatus.CONFIRMED || appointment.getCheckedInAt() != null
                    || !Objects.equals(appointment.getAppointmentDate(), message.appointmentDate())
                    || !Objects.equals(appointment.getStartTime(), message.startTime()) || !scheduledAt(appointment).isAfter(now)) return Optional.empty();
            return Optional.of(new Delivery(appointment.getUserId(), "Nhắc lịch hẹn Spa #" + appointment.getId(),
                    "Lịch hẹn Spa của bạn: " + appointment.getAppointmentDate() + " lúc " + appointment.getStartTime()
                            + " (giờ Việt Nam). Vui lòng liên hệ lễ tân nếu cần thay đổi lịch.", "SPA_APPOINTMENT_REMINDER"));
        }
        if (message.kind() != SpaNotificationMessage.Kind.FOLLOW_UP || message.instructionId() == null
                || appointment.getStatus() != AppointmentStatus.COMPLETED) return Optional.empty();
        Instruction instruction = instruction(message.instructionId());
        if (!Objects.equals(instruction.appointmentId(), appointment.getId()) || instruction.dueAt().isAfter(now)) return Optional.empty();
        return Optional.of(new Delivery(appointment.getUserId(), "Hướng dẫn sau buổi Spa #" + appointment.getId(), instruction.content(), "SPA_STAFF_FOLLOW_UP"));
    }

    private void recordDispatch(String key, Appointment appointment, SpaNotificationMessage.Kind kind, Long instructionId, Instant now) {
        update("INSERT INTO spa_notification_dispatches(dedup_key,appointment_id,notification_kind,appointment_date,start_time,instruction_id,enqueued_at) VALUES (?,?,?,?,?,?,?)",
                key, appointment.getId(), kind.name(), appointment.getAppointmentDate(), appointment.getStartTime(), instructionId, Timestamp.from(now));
    }

    private boolean dispatched(String key) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM spa_notification_dispatches WHERE dedup_key=?", Integer.class, key);
        return count != null && count > 0;
    }

    private int update(String sql, Object... parameters) { return SpaJdbcSupport.update(jdbc, sql, parameters); }

    private Appointment load(Long id, boolean lock) {
        return (lock ? appointments.findByIdForUpdate(id) : appointments.findByIdWithItems(id))
                .filter(appointment -> !Boolean.TRUE.equals(appointment.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
    }

    private Instant scheduledAt(Appointment appointment) {
        return appointment.getAppointmentDate().atTime(appointment.getStartTime()).atZone(SpaTimeRules.ZONE).toInstant();
    }

    private Instruction instruction(Long id) {
        List<Instruction> found = jdbc.query("SELECT * FROM spa_follow_up_instructions WHERE id=?", this::instructionRow, id);
        if (found.isEmpty()) throw new ResourceNotFoundException("Follow-up instruction not found");
        return found.get(0);
    }

    private Instruction instructionRow(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Instruction(rs.getLong("id"), rs.getLong("appointment_id"), rs.getLong("appointment_item_id"), rs.getString("content"),
                SpaJdbcSupport.instant(rs, "due_at"), rs.getLong("author_id"), SpaJdbcSupport.instant(rs, "created_at"));
    }

    @Schema(name = "SpaFollowUpInstructionCommand", description = "Hướng dẫn nhân viên viết sau item PERFORMED/LEGACY_FINALIZED; lưu không đồng nghĩa email đã gửi")
    public record InstructionCommand(@Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 4000, example = "Thực hiện chăm sóc theo hướng dẫn đã trao đổi tại buổi Spa.") String content,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Instant ISO-8601 có offset/UTC; từ hiện tại trừ dung sai một phút đến một năm", example = "2026-10-06T02:00:00Z") Instant dueAt) {}
    @Schema(name = "SpaFollowUpInstruction", description = "Bản hướng dẫn đã lưu; tác giả và thời điểm, chưa là bằng chứng email đã gửi")
    public record Instruction(Long id, Long appointmentId, Long appointmentItemId, String content, Instant dueAt, Long authorId, Instant createdAt) {}
}
