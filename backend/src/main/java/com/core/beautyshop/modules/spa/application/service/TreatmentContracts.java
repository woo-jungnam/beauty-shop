package com.core.beautyshop.modules.spa.application.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class TreatmentContracts {
    private TreatmentContracts() {}
    public record Plan(String name, String description, Long serviceId, BigDecimal price, Integer validityDays,
                       String terms, List<PlanSession> sessions) {}
    public record PlanSession(int sequence, String title, Integer minGapDays, Integer maxGapDays,
                              Boolean required, List<PlanItem> items) {}
    public record PlanItem(Long serviceId, String serviceName, Boolean required) {}
    public record TemplateView(Long id, Long versionId, int versionNumber, String status, String name,
                               String description, Long serviceId, Long packageId, BigDecimal price,
                               Integer validityDays, String terms, List<PlanSession> sessions, List<String> allowedActions) {}
    public record PurchaseCommand(Long templateId, Long versionId, Boolean acceptedTerms, String idempotencyKey, String notes) {}
    public record AssignCommand(Long staffId, String reason) {}
    public record IntakeCommand(String notes) {}
    public record ChangeCommand(Integer expectedVersion, String reason, Plan plan) {}
    public record DecisionCommand(Integer expectedVersion, String reason) {}
    public record RequestCommand(String type, String reason, Integer extensionDays) {}
    public record ResultCommand(String result, String aftercare, String internalNotes, Boolean sharedWithCustomer) {}
    public record CompleteCommand(String summary) {}
    public record RefundCommand(BigDecimal amount, String reference, String reason) {}
    public record ItemView(Long id, Long serviceId, String serviceName, Long ticketId, String status,
                           boolean required, Long appointmentItemId, Long appointmentId, String appointmentStatus,
                           String appointmentDate, String startTime, BigDecimal allocatedAmount, String result, String aftercare) {}
    public record SessionView(Long id, int sequence, String title, String status, int minGapDays,
                              Integer maxGapDays, boolean required, Instant earliestBookingAt, Instant latestBookingAt,
                              String blockedReason, Long appointmentId, String appointmentDate, String startTime,
                              String result, String aftercare, String internalNotes, Instant completedAt,
                              List<ItemView> items, List<String> allowedActions, List<ResultView> results) {}
    public record ResultView(Long id, Long authorId, String result, String aftercare, String internalNotes,
                             boolean sharedWithCustomer, Instant createdAt) {}
    public record ChangeView(Long id, String status, int expectedVersion, String reason, Plan plan, Plan oldPlan,
                             BigDecimal priceDifference, Long settlementOrderId, Long proposedBy, Long approvedBy,
                             Instant createdAt, Instant acceptedAt) {}
    public record RequestView(Long id, String type, String status, String reason, Integer extensionDays,
                              BigDecimal refundAmount, String refundReference, String reviewReason, Instant createdAt) {}
    public record AuditView(Long id, String action, Long actorId, String details, Instant createdAt) {}
    public record CourseView(Long id, Long userId, Long templateId, Long versionId, String name, String status,
                             int version, Long orderId, Long ticketId, Long assignedStaffId, Instant expiryDate,
                             String intakeNotes, String summary, BigDecimal paidAmount, int completedSessions,
                             int totalSessions, int progressPercent, List<SessionView> sessions,
                             List<ChangeView> changes, List<RequestView> requests, List<AuditView> audit,
                             List<String> allowedActions, Plan plan, Plan currentPlan) {}
    public record Report(long totalCourses, long pendingIntake, long activeCourses, long pausedCourses,
                         long completedCourses, long expiredCourses, long sessionsDue, long customersWithoutNextBooking,
                         BigDecimal collectedAmount, BigDecimal refundedAmount, BigDecimal unusedEntitlementAmount,
                         java.util.Map<String, Long> byStatus) {}
}
