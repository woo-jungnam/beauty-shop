package com.core.beautyshop.modules.spa.domain.treatment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;

/** Independent purchase and clinical records; published plans and historical results are never rewritten. */
public final class TreatmentEntities {
    private TreatmentEntities() {}

    @MappedSuperclass @Getter @Setter
    public abstract static class Identified {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) protected Long id;
    }

    @Entity(name = "TreatmentTemplate") @Table(name = "treatment_templates") @Getter @Setter
    public static class Template extends Identified {
        @Column(name = "service_id", nullable = false) private Long serviceId;
        @Column(name = "published_version_id") private Long publishedVersionId;
        @Column(name = "draft_version_id") private Long draftVersionId;
        @Column(nullable = false) private boolean archived;
        @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    }

    @Entity(name = "TreatmentTemplateVersion")
    @Table(name = "treatment_template_versions", uniqueConstraints = @UniqueConstraint(columnNames = {"template_id", "version_number"}))
    @Getter @Setter
    public static class TemplateVersion extends Identified {
        @Column(name = "template_id", nullable = false) private Long templateId;
        @Column(name = "version_number", nullable = false) private int versionNumber;
        @Column(name = "package_id", unique = true) private Long packageId;
        @Column(name = "plan_json", nullable = false, columnDefinition = "LONGTEXT") private String planJson;
        @Column(nullable = false, length = 30) private String status = "DRAFT";
        @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
        @Column(name = "published_at") private Instant publishedAt;
    }

    @Entity(name = "TreatmentPurchase")
    @Table(name = "treatment_purchases", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "idempotency_key"}))
    @Getter @Setter
    public static class Purchase {
        @Id @Column(name = "order_id") private Long orderId;
        @Column(name = "user_id", nullable = false) private Long userId;
        @Column(name = "template_id", nullable = false) private Long templateId;
        @Column(name = "version_id", nullable = false) private Long versionId;
        @Column(name = "idempotency_key", nullable = false, length = 64) private String idempotencyKey;
        @Column(name = "request_hash", nullable = false, length = 64) private String requestHash;
        @Column(name = "plan_json", nullable = false, columnDefinition = "LONGTEXT") private String planJson;
        @Column(name = "accepted_at", nullable = false) private Instant acceptedAt = Instant.now();
    }

    @Entity(name = "TreatmentCourse")
    @Table(name = "treatment_courses", uniqueConstraints = {@UniqueConstraint(columnNames = "order_id"), @UniqueConstraint(columnNames = "ticket_id")})
    @Getter @Setter
    public static class Course extends Identified {
        @Column(name = "user_id", nullable = false) private Long userId;
        @Column(name = "template_id", nullable = false) private Long templateId;
        @Column(name = "version_id", nullable = false) private Long versionId;
        @Column(name = "order_id", nullable = false) private Long orderId;
        @Column(name = "ticket_id", nullable = false) private Long ticketId;
        @Column(name = "assigned_staff_id") private Long assignedStaffId;
        @Column(name = "plan_json", nullable = false, columnDefinition = "LONGTEXT") private String planJson;
        @Column(name = "plan_revision", nullable = false) private int planRevision = 1;
        @Version @Column(name = "row_version", nullable = false) private long rowVersion;
        @Column(nullable = false, length = 30) private String status = "PENDING_INTAKE";
        @Column(name = "expiry_date") private Instant expiryDate;
        @Column(name = "intake_notes", columnDefinition = "TEXT") private String intakeNotes;
        @Column(columnDefinition = "TEXT") private String summary;
        @Column(name = "paid_amount", nullable = false, precision = 12, scale = 2) private BigDecimal paidAmount;
        @Column(name = "consumed_amount", nullable = false, precision = 12, scale = 2) private BigDecimal consumedAmount = BigDecimal.ZERO;
        @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
        @Column(name = "completed_at") private Instant completedAt;
    }

    @Entity(name = "TreatmentSession")
    @Table(name = "treatment_sessions", uniqueConstraints = @UniqueConstraint(columnNames = {"course_id", "sequence_number", "plan_revision"}))
    @Getter @Setter
    public static class Session extends Identified {
        @Column(name = "course_id", nullable = false) private Long courseId;
        @Column(name = "sequence_number", nullable = false) private int sequence;
        @Column(name = "plan_revision", nullable = false) private int planRevision = 1;
        @Column(nullable = false) private boolean retired;
        @Column(nullable = false, length = 150) private String title;
        @Column(name = "min_gap_days", nullable = false) private int minGapDays;
        @Column(name = "max_gap_days") private Integer maxGapDays;
        @Column(name = "is_required", nullable = false) private boolean required;
        @Column(nullable = false, length = 30) private String status = "PLANNED";
        @Column(name = "completed_at") private Instant completedAt;
        @Column(name = "result_text", columnDefinition = "TEXT") private String result;
        @Column(columnDefinition = "TEXT") private String aftercare;
        @Column(name = "internal_notes", columnDefinition = "TEXT") private String internalNotes;
        @Column(name = "shared_with_customer", nullable = false) private boolean sharedWithCustomer;
    }

    @Entity(name = "TreatmentSessionItem") @Table(name = "treatment_session_items") @Getter @Setter
    public static class SessionItem extends Identified {
        @Column(name = "session_id", nullable = false) private Long sessionId;
        @Column(name = "course_id", nullable = false) private Long courseId;
        @Column(nullable = false) private boolean retired;
        @Column(name = "service_id", nullable = false) private Long serviceId;
        @Column(name = "service_name", nullable = false, length = 150) private String serviceName;
        @Column(name = "is_required", nullable = false) private boolean required;
        @Column(nullable = false, length = 30) private String status = "PLANNED";
        @Column(name = "appointment_item_id", unique = true) private Long appointmentItemId;
        @Column(name = "completed_at") private Instant completedAt;
        @Column(name = "allocated_amount", nullable = false, precision = 12, scale = 2) private BigDecimal allocatedAmount;
    }

    @Entity(name = "TreatmentSessionResult") @Table(name = "treatment_session_results") @Getter @Setter
    public static class SessionResult extends Identified {
        @Column(name = "session_id", nullable = false) private Long sessionId;
        @Column(name = "author_id", nullable = false) private Long authorId;
        @Column(name = "result_text", nullable = false, columnDefinition = "TEXT") private String result;
        @Column(columnDefinition = "TEXT") private String aftercare;
        @Column(name = "internal_notes", columnDefinition = "TEXT") private String internalNotes;
        @Column(name = "shared_with_customer", nullable = false) private boolean sharedWithCustomer;
        @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    }

    @Entity(name = "TreatmentFinancialConsumption") @Table(name = "treatment_financial_consumptions") @Getter @Setter
    public static class FinancialConsumption extends Identified {
        @Column(name = "course_id", nullable = false) private Long courseId;
        @Column(name = "appointment_item_id", nullable = false, unique = true) private Long appointmentItemId;
        @Column(name = "session_item_id", nullable = false, unique = true) private Long sessionItemId;
        @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
        @Column(nullable = false, length = 30) private String kind;
        @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    }

    @Entity(name = "TreatmentChange") @Table(name = "treatment_changes") @Getter @Setter
    public static class Change extends Identified {
        @Column(name = "course_id", nullable = false) private Long courseId;
        @Column(name = "base_revision", nullable = false) private int baseRevision;
        @Column(name = "plan_json", nullable = false, columnDefinition = "LONGTEXT") private String planJson;
        @Column(name = "old_plan_json", nullable = false, columnDefinition = "LONGTEXT") private String oldPlanJson;
        @Column(nullable = false, length = 1000) private String reason;
        @Column(nullable = false, length = 30) private String status = "PENDING_APPROVAL";
        @Column(name = "price_difference", nullable = false, precision = 12, scale = 2) private BigDecimal priceDifference;
        @Column(name = "proposed_by", nullable = false) private Long proposedBy;
        @Column(name = "approved_by") private Long approvedBy;
        @Column(name = "accepted_by") private Long acceptedBy;
        @Column(name = "settlement_order_id") private Long settlementOrderId;
        @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
        @Column(name = "accepted_at") private Instant acceptedAt;
        @Column(name = "applied_at") private Instant appliedAt;
    }

    @Entity(name = "TreatmentWorkflowRequest") @Table(name = "treatment_workflow_requests") @Getter @Setter
    public static class WorkflowRequest extends Identified {
        @Column(name = "course_id", nullable = false) private Long courseId;
        @Column(nullable = false, length = 30) private String type;
        @Column(nullable = false, length = 30) private String status = "PENDING_APPROVAL";
        @Column(nullable = false, length = 1000) private String reason;
        @Column(name = "extension_days") private Integer extensionDays;
        @Column(name = "requested_by", nullable = false) private Long requestedBy;
        @Column(name = "reviewed_by") private Long reviewedBy;
        @Column(name = "review_reason", length = 1000) private String reviewReason;
        @Column(name = "refund_amount", nullable = false, precision = 12, scale = 2) private BigDecimal refundAmount = BigDecimal.ZERO;
        @Column(name = "refund_reference", length = 100) private String refundReference;
        @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
        @Column(name = "reviewed_at") private Instant reviewedAt;
    }

    @Entity(name = "TreatmentAudit") @Table(name = "treatment_audit") @Getter @Setter
    public static class Audit extends Identified {
        @Column(name = "course_id", nullable = false) private Long courseId;
        @Column(name = "actor_id") private Long actorId;
        @Column(nullable = false, length = 60) private String action;
        @Column(columnDefinition = "TEXT") private String details;
        @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    }
}
