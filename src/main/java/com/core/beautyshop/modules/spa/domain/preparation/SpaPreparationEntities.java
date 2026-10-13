package com.core.beautyshop.modules.spa.domain.preparation;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Schema metadata for Flyway validation and isolated create-drop environments.
 * The preparation service writes its append-only evidence through JDBC in the appointment transaction.
 */
public final class SpaPreparationEntities {
    private SpaPreparationEntities() {}

    @MappedSuperclass
    public abstract static class Identified {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "id", nullable = false, updatable = false)
        protected Long id;
    }

    @Entity(name = "SpaFormTemplateMetadata")
    @Table(name = "spa_form_templates")
    public static class FormTemplate extends Identified {
        @Column(name = "title", nullable = false, length = 150) private String title;
        @Column(name = "current_version", nullable = false) private int currentVersion;
        @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    }

    @Entity(name = "SpaFormVersionMetadata") @Immutable
    @Table(name = "spa_form_versions", uniqueConstraints = @UniqueConstraint(name = "uk_spa_form_version", columnNames = {"template_id", "version_number"}))
    public static class FormVersion extends Identified {
        @Column(name = "template_id", nullable = false) private Long templateId;
        @Column(name = "version_number", nullable = false) private int versionNumber;
        @Column(name = "title", nullable = false, length = 150) private String title;
        @Column(name = "questions_json", nullable = false, columnDefinition = "TEXT") private String questionsJson;
        @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    }

    @Entity(name = "SpaServicePreparationPolicyMetadata")
    @Table(name = "spa_service_preparation_policies")
    public static class ServicePolicy {
        @Id @Column(name = "service_id", nullable = false) private Long serviceId;
        @Column(name = "warnings_required", nullable = false) private boolean warningsRequired;
        @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    }

    @Entity(name = "SpaServiceFormRequirementMetadata")
    @Table(name = "spa_service_form_requirements", uniqueConstraints = @UniqueConstraint(name = "uk_spa_service_form", columnNames = {"service_id", "form_version_id"}))
    public static class ServiceFormRequirement extends Identified {
        @Column(name = "service_id", nullable = false) private Long serviceId;
        @Column(name = "form_version_id", nullable = false) private Long formVersionId;
    }

    @Entity(name = "SpaPreparationSnapshotMetadata") @Immutable
    @Table(name = "spa_preparation_snapshots")
    public static class PreparationSnapshot extends Identified {
        @Column(name = "appointment_item_id", nullable = false, unique = true) private Long appointmentItemId;
        @Column(name = "warnings_required", nullable = false) private boolean warningsRequired;
        @Column(name = "snapshot_at", nullable = false, updatable = false) private Instant snapshotAt;
    }

    @Entity(name = "SpaFormRequirementSnapshotMetadata") @Immutable
    @Table(name = "spa_form_requirement_snapshots", uniqueConstraints = @UniqueConstraint(name = "uk_spa_snapshot_form", columnNames = {"preparation_id", "form_version_id"}))
    public static class FormRequirementSnapshot extends Identified {
        @Column(name = "preparation_id", nullable = false) private Long preparationId;
        @Column(name = "form_version_id", nullable = false) private Long formVersionId;
    }

    @Entity(name = "SpaFormResponseMetadata") @Immutable
    @Table(name = "spa_form_responses", indexes = @Index(name = "idx_spa_response_latest", columnList = "requirement_id,id"))
    @org.hibernate.annotations.Check(constraints = "customer_id=actor_id AND acknowledged=true")
    public static class FormResponse extends Identified {
        @Column(name = "requirement_id", nullable = false) private Long requirementId;
        @Column(name = "customer_id", nullable = false) private Long customerId;
        @Column(name = "actor_id", nullable = false) private Long actorId;
        @Column(name = "acknowledged", nullable = false) private boolean acknowledged;
        @Column(name = "answers_json", nullable = false, columnDefinition = "TEXT") private String answersJson;
        @Column(name = "submitted_at", nullable = false, updatable = false) private Instant submittedAt;
    }

    @Entity(name = "SpaWarningAcknowledgmentMetadata") @Immutable
    @Table(name = "spa_warning_acknowledgments", indexes = @Index(name = "idx_spa_warning_evidence", columnList = "preparation_id,actor_id,summary_hash"))
    public static class WarningAcknowledgment extends Identified {
        @Column(name = "preparation_id", nullable = false) private Long preparationId;
        @Column(name = "actor_id", nullable = false) private Long actorId;
        @Column(name = "summary_hash", nullable = false, length = 64) private String summaryHash;
        @Column(name = "acknowledged_at", nullable = false, updatable = false) private Instant acknowledgedAt;
    }

    @Entity(name = "SpaPreparationOverrideMetadata") @Immutable
    @Table(name = "spa_preparation_overrides", indexes = @Index(name = "idx_spa_override_history", columnList = "preparation_id,id"))
    public static class PreparationOverride extends Identified {
        @Column(name = "preparation_id", nullable = false) private Long preparationId;
        @Column(name = "actor_id", nullable = false) private Long actorId;
        @Column(name = "reason", nullable = false, length = 1000) private String reason;
        @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    }

    @Entity(name = "SpaFollowUpInstructionMetadata") @Immutable
    @Table(name = "spa_follow_up_instructions", indexes = @Index(name = "idx_spa_follow_up_due", columnList = "due_at,appointment_id"))
    public static class FollowUpInstruction extends Identified {
        @Column(name = "appointment_id", nullable = false) private Long appointmentId;
        @Column(name = "appointment_item_id", nullable = false) private Long appointmentItemId;
        @Column(name = "content", nullable = false, columnDefinition = "TEXT") private String content;
        @Column(name = "due_at", nullable = false) private Instant dueAt;
        @Column(name = "author_id", nullable = false) private Long authorId;
        @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    }

    @Entity(name = "SpaNotificationDispatchMetadata") @Immutable
    @Table(name = "spa_notification_dispatches", indexes = {
            @Index(name = "idx_spa_dispatch_reminder", columnList = "appointment_id,notification_kind,appointment_date,start_time"),
            @Index(name = "idx_spa_dispatch_follow_up", columnList = "instruction_id,notification_kind")})
    public static class NotificationDispatch {
        @Id @Column(name = "dedup_key", nullable = false, length = 180) private String dedupKey;
        @Column(name = "appointment_id", nullable = false) private Long appointmentId;
        @Column(name = "notification_kind", nullable = false, length = 30) private String notificationKind;
        @Column(name = "appointment_date", nullable = false) private LocalDate appointmentDate;
        @Column(name = "start_time", nullable = false) private LocalTime startTime;
        @Column(name = "instruction_id") private Long instructionId;
        @Column(name = "enqueued_at", nullable = false, updatable = false) private Instant enqueuedAt;
    }
}
