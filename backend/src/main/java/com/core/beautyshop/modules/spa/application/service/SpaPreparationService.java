package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.crm.api.CustomerCareFacade;
import com.core.beautyshop.modules.spa.domain.Appointment;
import com.core.beautyshop.modules.spa.domain.AppointmentItem;
import com.core.beautyshop.modules.spa.domain.AppointmentRepository;
import com.core.beautyshop.modules.spa.domain.BeautyServiceRepository;
import com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus;
import com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import com.core.beautyshop.shared.dto.PageResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.sql.Statement;
import java.time.Instant;
import java.util.*;
import io.swagger.v3.oas.annotations.media.Schema;

/** Versioned, non-clinical questionnaires and explicit customer consent. All evidence is append-only. */
@Service
@RequiredArgsConstructor
public class SpaPreparationService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AppointmentRepository appointments;
    private final BeautyServiceRepository services;
    private final SpaAccessService access;
    private final CustomerCareFacade care;

    @Transactional
    public FormVersion createTemplate(FormCommand command) {
        requireManager();
        validateForm(command);
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO spa_form_templates(title,current_version,created_at) VALUES (?,0,?)", Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, command.title().trim());
            SpaJdbcSupport.bind(statement, 2, Instant.now());
            return statement;
        }, key);
        Long id = Objects.requireNonNull(key.getKey()).longValue();
        return addVersion(id, command);
    }

    @Transactional
    public FormVersion addVersion(Long templateId, FormCommand command) {
        requireManager();
        validateForm(command);
        List<Integer> versions = jdbc.query("SELECT current_version FROM spa_form_templates WHERE id=? FOR UPDATE",
                (rs, row) -> rs.getInt(1), templateId);
        if (versions.isEmpty()) throw new ResourceNotFoundException("Form template not found");
        int version = versions.get(0) + 1;
        Instant createdAt = Instant.now();
        update("INSERT INTO spa_form_versions(template_id,version_number,title,questions_json,created_at) VALUES (?,?,?,?,?)",
                templateId, version, command.title().trim(), encode(command.questions()), Timestamp.from(createdAt));
        update("UPDATE spa_form_templates SET current_version=?,title=? WHERE id=?", version, command.title().trim(), templateId);
        return jdbc.query("SELECT * FROM spa_form_versions WHERE template_id=? AND version_number=?",
                (rs, row) -> formVersion(rs.getLong("id"), templateId, version, rs.getString("title"),
                        rs.getString("questions_json"), SpaJdbcSupport.instant(rs, "created_at")), templateId, version).get(0);
    }

    @Transactional(readOnly = true)
    public PageResponse<FormVersion> templates(int page, int size) {
        requireManager();
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new BusinessException("Page must be nonnegative and size must be between 1 and 100");
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM spa_form_templates", Long.class);
        List<FormVersion> current = jdbc.query("SELECT v.* FROM spa_form_versions v JOIN spa_form_templates t "
                        + "ON v.template_id=t.id AND v.version_number=t.current_version ORDER BY t.id DESC LIMIT ? OFFSET ?",
                (rs, row) -> formVersion(rs.getLong("id"), rs.getLong("template_id"), rs.getInt("version_number"),
                        rs.getString("title"), rs.getString("questions_json"), SpaJdbcSupport.instant(rs, "created_at")), size, (long) page * size);
        return PageResponse.of(new PageImpl<>(current, PageRequest.of(page, size), total == null ? 0 : total));
    }

    @Transactional(readOnly = true)
    public List<FormVersion> versions(Long templateId) {
        requireManager();
        return jdbc.query("SELECT * FROM spa_form_versions WHERE template_id=? ORDER BY version_number DESC",
                (rs, row) -> formVersion(rs.getLong("id"), templateId, rs.getInt("version_number"), rs.getString("title"),
                        rs.getString("questions_json"), SpaJdbcSupport.instant(rs, "created_at")), templateId);
    }

    @Transactional
    public ServicePolicy configureService(Long serviceId, ServicePolicyCommand command) {
        requireManager();
        services.findByIdForUpdate(serviceId).orElseThrow(() -> new ResourceNotFoundException("Service not found"));
        if (command == null || command.requiredFormVersionIds() == null || command.requiredFormVersionIds().size() > 20
                || command.requiredFormVersionIds().stream().anyMatch(Objects::isNull)
                || new HashSet<>(command.requiredFormVersionIds()).size() != command.requiredFormVersionIds().size()) {
            throw new BusinessException("Select up to 20 distinct form versions");
        }
        Set<Long> templateIds = new HashSet<>();
        for (Long versionId : command.requiredFormVersionIds()) {
            FormVersion version = version(versionId);
            if (!templateIds.add(version.templateId())) throw new BusinessException("Only one version of each template may be required");
        }
        int changed = update("UPDATE spa_service_preparation_policies SET warnings_required=?,updated_at=? WHERE service_id=?",
                command.warningsRequired(), Timestamp.from(Instant.now()), serviceId);
        if (changed == 0) update("INSERT INTO spa_service_preparation_policies(service_id,warnings_required,updated_at) VALUES (?,?,?)",
                serviceId, command.warningsRequired(), Timestamp.from(Instant.now()));
        update("DELETE FROM spa_service_form_requirements WHERE service_id=?", serviceId);
        for (Long versionId : command.requiredFormVersionIds()) {
            update("INSERT INTO spa_service_form_requirements(service_id,form_version_id) VALUES (?,?)", serviceId, versionId);
        }
        return policy(serviceId);
    }

    @Transactional(readOnly = true)
    public ServicePolicy policy(Long serviceId) {
        requireManager();
        return new ServicePolicy(serviceId, warningPolicy(serviceId), jdbc.query(
                "SELECT form_version_id FROM spa_service_form_requirements WHERE service_id=? ORDER BY form_version_id",
                (rs, row) -> version(rs.getLong(1)), serviceId));
    }

    /** Called once after booking has persisted item IDs. Even an empty policy gets a snapshot marker. */
    @Transactional
    public void snapshotRequirements(Appointment appointment) {
        for (AppointmentItem item : appointment.getItems()) {
            if (Boolean.TRUE.equals(item.getIsDeleted())) continue;
            if (item.getId() == null) throw new BusinessException("Persist appointment items before snapshotting forms");
            if (!snapshotIds(item.getId()).isEmpty()) continue;
            // Serialize policy changes with booking; the core also locks service rows when booking.
            services.findByIdForUpdate(item.getService().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found"));
            update("INSERT INTO spa_preparation_snapshots(appointment_item_id,warnings_required,snapshot_at) VALUES (?,?,?)",
                    item.getId(), warningPolicy(item.getService().getId()), Timestamp.from(Instant.now()));
            Long snapshotId = snapshotIds(item.getId()).get(0);
            List<Long> requiredVersions = jdbc.query("SELECT form_version_id FROM spa_service_form_requirements WHERE service_id=?",
                    (rs, row) -> rs.getLong(1), item.getService().getId());
            for (Long versionId : requiredVersions) update(
                    "INSERT INTO spa_form_requirement_snapshots(preparation_id,form_version_id) VALUES (?,?)", snapshotId, versionId);
        }
    }

    @Transactional(readOnly = true)
    public CareSummary careSummary(Long appointmentId) {
        Appointment appointment = appointment(appointmentId, false);
        access.requireCanViewCareSummary(appointment);
        return summary(appointment);
    }

    @Transactional(readOnly = true)
    public List<ItemPreparation> preparation(Long appointmentId) {
        Appointment appointment = appointment(appointmentId, false);
        access.requireCanViewAppointment(appointment);
        return appointment.getItems().stream().filter(item -> !Boolean.TRUE.equals(item.getIsDeleted()))
                .map(this::itemPreparation).toList();
    }

    @Transactional
    public FormResponse submit(Long appointmentId, Long requirementId, ResponseCommand command) {
        Appointment appointment = appointment(appointmentId, true);
        if ((!access.hasRole("CUSTOMER") && !access.hasRole("USER"))
                || !Objects.equals(appointment.getUserId(), SecurityUtils.getCurrentUserId())) {
            throw new AccessDeniedException("Only the appointment customer may submit their consent");
        }
        requirePreparationOpen(appointment);
        Requirement requirement = requirementForAppointment(appointment, requirementId);
        requireItemPreparationOpen(item(appointment, requirement.appointmentItemId()));
        if (command == null || !command.acknowledged()) throw new BusinessException("Customer acknowledgment is required");
        validateAnswers(requirement.form(), command.answers());
        Instant now = Instant.now();
        Long actor = SecurityUtils.getCurrentUserId();
        update("INSERT INTO spa_form_responses(requirement_id,customer_id,actor_id,acknowledged,answers_json,submitted_at) VALUES (?,?,?,?,?,?)",
                requirementId, appointment.getUserId(), actor, true, encode(command.answers()), Timestamp.from(now));
        return latestResponse(requirementId);
    }

    @Transactional
    public WarningAcknowledgment acknowledgeWarnings(Long appointmentId, Long itemId, WarningCommand command) {
        Appointment appointment = appointment(appointmentId, true);
        requirePreparationOpen(appointment);
        AppointmentItem item = item(appointment, itemId);
        requireItemPreparationOpen(item);
        access.requireCanPerformItem(item);
        Long snapshotId = requireSnapshot(itemId);
        CareSummary current = summary(appointment);
        if (command == null || !Objects.equals(current.versionHash(), command.versionHash())) {
            throw new BusinessException("Care summary has changed; review the current summary before acknowledging");
        }
        Instant now = Instant.now();
        Long actor = SecurityUtils.getCurrentUserId();
        update("INSERT INTO spa_warning_acknowledgments(preparation_id,actor_id,summary_hash,acknowledged_at) VALUES (?,?,?,?)",
                snapshotId, actor, current.versionHash(), Timestamp.from(now));
        return new WarningAcknowledgment(actor, current.versionHash(), now);
    }

    @Transactional
    public OverrideEvidence override(Long appointmentId, Long itemId, OverrideCommand command) {
        requireManager();
        Appointment appointment = appointment(appointmentId, true);
        requirePreparationOpen(appointment);
        requireItemPreparationOpen(item(appointment, itemId));
        Long snapshotId = requireSnapshot(itemId);
        if (command == null || command.reason() == null || command.reason().isBlank() || command.reason().length() > 1000) {
            throw new BusinessException("A manager override reason of up to 1000 characters is required");
        }
        String reason = command.reason().trim();
        Long actor = SecurityUtils.getCurrentUserId();
        Instant now = Instant.now();
        update("INSERT INTO spa_preparation_overrides(preparation_id,actor_id,reason,created_at) VALUES (?,?,?,?)",
                snapshotId, actor, reason, Timestamp.from(now));
        return new OverrideEvidence(actor, reason, now);
    }

    /** Core calls this under its appointment lock immediately before moving an item to IN_PROGRESS. */
    @Transactional(readOnly = true)
    public void requireReadyForStart(AppointmentItem item) {
        access.requireCanPerformItem(item);
        List<Long> snapshots = snapshotIds(item.getId());
        if (snapshots.isEmpty()) return; // Legacy bookings have no automatically imposed new requirements.
        Long snapshotId = snapshots.get(0);
        if (!overrides(snapshotId).isEmpty()) return;
        for (Requirement requirement : requirements(snapshotId)) {
            FormResponse response = latestResponse(requirement.id());
            if (response == null || !response.acknowledged()
                    || !Objects.equals(response.customerId(), item.getAppointment().getUserId())
                    || !Objects.equals(response.actorId(), response.customerId())) {
                throw new BusinessException("The customer must complete and acknowledge every required form before service starts");
            }
        }
        Boolean warnings = jdbc.queryForObject("SELECT warnings_required FROM spa_preparation_snapshots WHERE id=?", Boolean.class, snapshotId);
        if (Boolean.TRUE.equals(warnings)) {
            String hash = summary(item.getAppointment()).versionHash();
            Long actor = SecurityUtils.getCurrentUserId();
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM spa_warning_acknowledgments WHERE preparation_id=? AND actor_id=? AND summary_hash=?",
                    Integer.class, snapshotId, actor, hash);
            if (count == null || count == 0) throw new BusinessException("Review and acknowledge current care warnings before starting this service");
        }
    }

    private ItemPreparation itemPreparation(AppointmentItem item) {
        List<Long> snapshot = snapshotIds(item.getId());
        if (snapshot.isEmpty()) return new ItemPreparation(item.getId(), true, false, List.of(), List.of(), List.of());
        Long id = snapshot.get(0);
        Boolean warning = jdbc.queryForObject("SELECT warnings_required FROM spa_preparation_snapshots WHERE id=?", Boolean.class, id);
        List<RequirementView> forms = requirements(id).stream()
                .map(required -> new RequirementView(required.id(), required.form(), latestResponse(required.id()))).toList();
        List<WarningAcknowledgment> acknowledgments = jdbc.query(
                "SELECT actor_id,summary_hash,acknowledged_at FROM spa_warning_acknowledgments WHERE preparation_id=? ORDER BY id DESC",
                (rs, row) -> new WarningAcknowledgment(rs.getLong(1), rs.getString(2), SpaJdbcSupport.instant(rs, 3)), id);
        return new ItemPreparation(item.getId(), false, Boolean.TRUE.equals(warning), forms, acknowledgments, overrides(id));
    }

    private List<OverrideEvidence> overrides(Long snapshotId) {
        return jdbc.query("SELECT actor_id,reason,created_at FROM spa_preparation_overrides WHERE preparation_id=? ORDER BY id DESC",
                (rs, row) -> new OverrideEvidence(rs.getLong(1), rs.getString(2), SpaJdbcSupport.instant(rs, 3)), snapshotId);
    }

    private List<Requirement> requirements(Long snapshotId) {
        return jdbc.query("SELECT r.id,r.form_version_id,p.appointment_item_id FROM spa_form_requirement_snapshots r "
                        + "JOIN spa_preparation_snapshots p ON p.id=r.preparation_id WHERE r.preparation_id=? ORDER BY r.id",
                (rs, row) -> new Requirement(rs.getLong(1), version(rs.getLong(2)), rs.getLong(3)), snapshotId);
    }

    private Requirement requirementForAppointment(Appointment appointment, Long requirementId) {
        for (AppointmentItem item : appointment.getItems()) {
            if (Boolean.TRUE.equals(item.getIsDeleted())) continue;
            for (Long snapshotId : snapshotIds(item.getId())) {
                for (Requirement requirement : requirements(snapshotId)) if (Objects.equals(requirement.id(), requirementId)) return requirement;
            }
        }
        throw new ResourceNotFoundException("This form requirement does not belong to the appointment");
    }

    private FormResponse latestResponse(Long requirementId) {
        List<FormResponse> responses = jdbc.query("SELECT * FROM spa_form_responses WHERE requirement_id=? ORDER BY id DESC LIMIT 1",
                (rs, row) -> new FormResponse(rs.getLong("id"), requirementId, rs.getLong("customer_id"), rs.getLong("actor_id"),
                        rs.getBoolean("acknowledged"), decodeAnswers(rs.getString("answers_json")), SpaJdbcSupport.instant(rs, "submitted_at")), requirementId);
        return responses.isEmpty() ? null : responses.get(0);
    }

    private CareSummary summary(Appointment appointment) {
        List<CustomerCareFacade.CareNote> notes = care.getCareNotes(appointment.getUserId()).stream()
                .sorted(Comparator.comparing(CustomerCareFacade.CareNote::id)).toList();
        List<CustomerFormSummary> forms = appointment.getItems().stream()
                .filter(item -> !Boolean.TRUE.equals(item.getIsDeleted())).sorted(Comparator.comparing(AppointmentItem::getId))
                .map(item -> new CustomerFormSummary(item.getId(), snapshotIds(item.getId()).stream()
                        .flatMap(snapshot -> requirements(snapshot).stream())
                        .map(required -> new RequirementView(required.id(), required.form(), latestResponse(required.id()))).toList()))
                .toList();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(encode(new CareSummaryVersion(
                    appointment.getId(), appointment.getUserId(), notes, forms)).getBytes(StandardCharsets.UTF_8));
            return new CareSummary(appointment.getId(), appointment.getUserId(), HexFormat.of().formatHex(digest), notes, forms);
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private Appointment appointment(Long appointmentId, boolean lock) {
        Appointment appointment = (lock ? appointments.findByIdForUpdate(appointmentId) : appointments.findByIdWithItems(appointmentId))
                .filter(found -> !Boolean.TRUE.equals(found.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
        return appointment;
    }

    private AppointmentItem item(Appointment appointment, Long itemId) {
        return appointment.getItems().stream().filter(found -> Objects.equals(found.getId(), itemId) && !Boolean.TRUE.equals(found.getIsDeleted()))
                .findFirst().orElseThrow(() -> new ResourceNotFoundException("Appointment item not found"));
    }

    private void requirePreparationOpen(Appointment appointment) {
        if (appointment.getStatus() != AppointmentStatus.PENDING && appointment.getStatus() != AppointmentStatus.CONFIRMED
                && appointment.getStatus() != AppointmentStatus.IN_PROGRESS) {
            throw new BusinessException("Preparation evidence may only be recorded before service starts");
        }
    }

    private void requireItemPreparationOpen(AppointmentItem item) {
        if (item.getActualStartedAt() != null || item.getExecutionStatus() != AppointmentItemExecutionStatus.PLANNED) {
            throw new BusinessException("Preparation evidence is immutable once this service item has started or finished");
        }
    }

    private List<Long> snapshotIds(Long itemId) {
        return jdbc.query("SELECT id FROM spa_preparation_snapshots WHERE appointment_item_id=?", (rs, row) -> rs.getLong(1), itemId);
    }

    private Long requireSnapshot(Long itemId) {
        List<Long> ids = snapshotIds(itemId);
        if (ids.isEmpty()) throw new BusinessException("This legacy booking has no preparation requirements");
        return ids.get(0);
    }

    private boolean warningPolicy(Long serviceId) {
        List<Boolean> rows = jdbc.query("SELECT warnings_required FROM spa_service_preparation_policies WHERE service_id=?", (rs, row) -> rs.getBoolean(1), serviceId);
        return !rows.isEmpty() && rows.get(0);
    }

    private FormVersion version(Long id) {
        List<FormVersion> versions = jdbc.query("SELECT * FROM spa_form_versions WHERE id=?",
                (rs, row) -> formVersion(id, rs.getLong("template_id"), rs.getInt("version_number"), rs.getString("title"),
                        rs.getString("questions_json"), SpaJdbcSupport.instant(rs, "created_at")), id);
        if (versions.isEmpty()) throw new ResourceNotFoundException("Form version not found: " + id);
        return versions.get(0);
    }

    private FormVersion formVersion(Long id, Long templateId, int version, String title, String questions, Instant createdAt) {
        try { return new FormVersion(id, templateId, version, title, json.readValue(questions, new TypeReference<List<FormQuestion>>() {}), createdAt); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Stored form definition is invalid", exception); }
    }

    private void requireManager() {
        if (!access.hasRole("ADMIN") && !access.hasRole("STAFF")) throw new AccessDeniedException("Only administrators and staff may manage preparation policies or override consent");
    }

    private void validateForm(FormCommand command) {
        if (command == null || command.title() == null || command.title().isBlank() || command.title().length() > 150
                || command.questions() == null || command.questions().isEmpty() || command.questions().size() > 50) {
            throw new BusinessException("A title of up to 150 characters and 1 to 50 questions are required");
        }
        Set<String> keys = new HashSet<>();
        for (FormQuestion question : command.questions()) {
            if (question == null || question.key() == null || !question.key().matches("[A-Za-z][A-Za-z0-9_]{0,49}")
                    || !keys.add(question.key()) || question.label() == null || question.label().isBlank()
                    || question.label().length() > 500 || question.type() == null) {
                throw new BusinessException("Each question requires a unique key, label and supported answer type");
            }
        }
    }

    private void validateAnswers(FormVersion form, Map<String, Object> answers) {
        if (answers == null || answers.size() > 50 || encode(answers).length() > 30000) throw new BusinessException("Invalid questionnaire answers");
        Set<String> keys = new HashSet<>();
        for (FormQuestion question : form.questions()) {
            keys.add(question.key());
            Object answer = answers.get(question.key());
            boolean empty = answer == null || (answer instanceof String text && text.isBlank());
            if (question.required() && empty) throw new BusinessException("Required answer missing: " + question.key());
            if (empty) continue;
            if ((question.type() == QuestionType.BOOLEAN && !(answer instanceof Boolean))
                    || (question.type() == QuestionType.TEXT && (!(answer instanceof String) || ((String) answer).length() > 4000))) {
                throw new BusinessException("Invalid answer type or length: " + question.key());
            }
        }
        if (!keys.containsAll(answers.keySet())) throw new BusinessException("Answers must match the booked form version");
    }

    private String encode(Object object) {
        try { return json.writeValueAsString(object); }
        catch (JsonProcessingException exception) { throw new BusinessException("Invalid questionnaire data"); }
    }

    private int update(String sql, Object... parameters) { return SpaJdbcSupport.update(jdbc, sql, parameters); }

    private Map<String, Object> decodeAnswers(String value) {
        try { return json.readValue(value, new TypeReference<Map<String, Object>>() {}); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Stored questionnaire response is invalid", exception); }
    }

    public enum QuestionType { TEXT, BOOLEAN }
    @Schema(name = "SpaFormQuestion", description = "Một câu hỏi TEXT/BOOLEAN; key dùng trong answers của khách")
    public record FormQuestion(@Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = "[A-Za-z][A-Za-z0-9_]{0,49}", example = "skinGoals") String key,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 500, example = "Mục tiêu chăm sóc của bạn?") String label,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "TEXT") QuestionType type,
            @Schema(description = "true thì khách phải trả lời, false là tùy chọn", example = "true") boolean required) {}
    @Schema(name = "SpaFormCommand", description = "Tạo template/version; version đã lưu bất biến")
    public record FormCommand(@Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 150, example = "Thông tin trước buổi chăm sóc") String title,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "1–50 câu hỏi, key duy nhất") List<FormQuestion> questions) {}
    @Schema(name = "SpaFormVersion", description = "Phiên bản bất biến của form; id là formVersionId dùng trong cấu hình service")
    public record FormVersion(Long id, Long templateId, int version, String title, List<FormQuestion> questions, Instant createdAt) {}
    @Schema(name = "SpaServicePreparationPolicyCommand", description = "Thay toàn bộ policy cho booking mới; không sửa preparation snapshot đã đặt")
    public record ServicePolicyCommand(@Schema(description = "Nếu true, actor START phải ack care-summary hiện tại; mặc định false", example = "false") boolean warningsRequired,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Tối đa 20 ID version, không trùng, một version mỗi template; [] bỏ form", example = "[1]") List<Long> requiredFormVersionIds) {}
    @Schema(name = "SpaServicePreparationPolicy", description = "Policy hiện tại của dịch vụ, dùng cho booking mới")
    public record ServicePolicy(Long serviceId, boolean warningsRequired, List<FormVersion> requiredForms) {}
    @Schema(name = "SpaCustomerFormResponseCommand", description = "Chỉ khách sở hữu tự nộp consent; tạo bản mới, không ghi đè lịch sử")
    public record ResponseCommand(@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Phải là true; chưa hỗ trợ từ chối hoặc rút consent", example = "true") boolean acknowledged,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Map questionKey→string/boolean theo booked form version. TEXT tối đa 4000 ký tự; không có key ngoài form, tối đa 50 key và JSON tối đa 30000 ký tự", example = "{\"skinGoals\":\"Dưỡng ẩm\"}") Map<String, Object> answers) {}
    @Schema(name = "SpaCustomerFormResponse", description = "Bằng chứng append-only; customerId=actorId, acknowledged=true")
    public record FormResponse(Long id, Long requirementId, Long customerId, Long actorId, boolean acknowledged, Map<String, Object> answers, Instant submittedAt) {}
    @Schema(name = "SpaCareWarningAcknowledgmentCommand", description = "Người thực hiện gửi hash của care-summary vừa đọc")
    public record WarningCommand(@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "SHA-256 hiện tại từ care-summary, hồ sơ/form đổi thì hash cũ bị từ chối", minLength = 64, maxLength = 64, example = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef") String versionHash) {}
    @Schema(name = "SpaCareWarningAcknowledgment", description = "Actor đã xác nhận đọc care-summary ở versionHash này; chưa là consent của khách")
    public record WarningAcknowledgment(Long actorId, String versionHash, Instant acknowledgedAt) {}
    @Schema(name = "SpaPreparationOverrideCommand", description = "ADMIN bỏ qua toàn bộ yêu cầu chuẩn bị của item; không tạo consent giả")
    public record OverrideCommand(@Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 1000, example = "Ngoại lệ được quản lý duyệt và ghi nhận") String reason) {}
    @Schema(name = "SpaPreparationOverrideEvidence", description = "Ngoại lệ append-only của ADMIN; chưa có API revoke trước START")
    public record OverrideEvidence(Long actorId, String reason, Instant createdAt) {}
    @Schema(name = "SpaAppointmentCareSummary", description = "Dữ liệu chăm sóc nội bộ và form của lịch; khách không được đọc qua care-summary")
    public record CareSummary(Long appointmentId, Long customerId, String versionHash, List<CustomerCareFacade.CareNote> notes,
                              List<CustomerFormSummary> forms) {}
    @Schema(name = "SpaCustomerItemFormSummary", description = "Các form đã chụp cho một item")
    public record CustomerFormSummary(Long appointmentItemId, List<RequirementView> forms) {}
    @Schema(name = "SpaItemFormRequirement", description = "id là requirementId dùng để nộp form, khác form.id/formVersionId; response chỉ bản mới nhất")
    public record RequirementView(Long id, FormVersion form, FormResponse latestResponse) {}
    @Schema(name = "SpaItemPreparation", description = "Snapshot yêu cầu và lịch sử ack/override; legacyBooking=true không tự áp form mới")
    public record ItemPreparation(Long appointmentItemId, boolean legacyBooking, boolean warningsRequired, List<RequirementView> forms,
                                  List<WarningAcknowledgment> warningAcknowledgments, List<OverrideEvidence> overrides) {}
    private record Requirement(Long id, FormVersion form, Long appointmentItemId) {}
    private record CareSummaryVersion(Long appointmentId, Long customerId, List<CustomerCareFacade.CareNote> notes,
                                      List<CustomerFormSummary> forms) {}
}
