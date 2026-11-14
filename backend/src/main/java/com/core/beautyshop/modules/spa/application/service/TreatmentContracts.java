package com.core.beautyshop.modules.spa.application.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class TreatmentContracts {
    private TreatmentContracts() {}
    public record Plan(
        @NotBlank(message = "Tên phác đồ không được để trống") @Size(max = 150, message = "Tên phác đồ tối đa 150 ký tự") String name,
        @Size(max = 10000, message = "Mô tả tối đa 10000 ký tự") String description,
        @NotNull(message = "Dịch vụ cha là bắt buộc") Long serviceId,
        @NotNull(message = "Giá là bắt buộc") @DecimalMin(value = "1.00", message = "Giá phải lớn hơn 0") BigDecimal price,
        @Min(value = 1, message = "Hạn tối thiểu 1 ngày") @Max(value = 3650, message = "Hạn tối đa 3650 ngày") Integer validityDays,
        @NotBlank(message = "Điều khoản là bắt buộc") @Size(max = 10000, message = "Điều khoản tối đa 10000 ký tự") String terms,
        @NotEmpty(message = "Danh sách buổi không được để trống") List<@Valid PlanSession> sessions
    ) {}
    public record PlanSession(
        int sequence,
        @NotBlank(message = "Tiêu đề buổi là bắt buộc") @Size(max = 150, message = "Tiêu đề buổi tối đa 150 ký tự") String title,
        @Min(value = 0, message = "Khoảng cách ngày tối thiểu không âm") @Max(value = 3650, message = "Khoảng cách tối đa 3650 ngày") Integer minGapDays,
        @Min(value = 0, message = "Khoảng cách ngày tối đa không âm") @Max(value = 3650, message = "Khoảng cách tối đa 3650 ngày") Integer maxGapDays,
        Boolean required,
        @NotEmpty(message = "Mỗi buổi cần ít nhất một dịch vụ") List<@Valid PlanItem> items
    ) {}
    public record PlanItem(
        @NotNull(message = "Mã dịch vụ là bắt buộc") Long serviceId,
        String serviceName,
        Boolean required
    ) {}
    public record TemplateView(Long id, Long versionId, int versionNumber, String status, String name,
                               String description, Long serviceId, Long packageId, BigDecimal price,
                               Integer validityDays, String terms, List<PlanSession> sessions, List<String> allowedActions) {}
    public record PurchaseCommand(
        @NotNull(message = "Mã mẫu phác đồ là bắt buộc") Long templateId,
        @NotNull(message = "Phiên bản phác đồ là bắt buộc") Long versionId,
        @AssertTrue(message = "Cần chấp nhận điều khoản phác đồ") Boolean acceptedTerms,
        @NotBlank(message = "Khóa phân biệt trùng lặp là bắt buộc") @Size(max = 64, message = "Khóa trùng lặp tối đa 64 ký tự") String idempotencyKey,
        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String notes
    ) {}
    public record AssignCommand(
        @NotNull(message = "Mã nhân viên phụ trách là bắt buộc") Long staffId,
        @Size(max = 1000, message = "Lý do tối đa 1000 ký tự") String reason
    ) {}
    public record IntakeCommand(
        @Size(max = 4000, message = "Ghi chú tiếp nhận tối đa 4000 ký tự") String notes
    ) {}
    public record ChangeCommand(
        @NotNull(message = "Phiên bản kỳ vọng là bắt buộc") Integer expectedVersion,
        @NotBlank(message = "Lý do điều chỉnh là bắt buộc") @Size(max = 1000, message = "Lý do tối đa 1000 ký tự") String reason,
        @NotNull(message = "Kế hoạch phác đồ mới là bắt buộc") @Valid Plan plan
    ) {}
    public record DecisionCommand(
        Integer expectedVersion,
        @Size(max = 1000, message = "Lý do quyết định tối đa 1000 ký tự") String reason
    ) {}
    public record RequestCommand(
        @NotBlank(message = "Loại yêu cầu là bắt buộc") String type,
        @NotBlank(message = "Lý do là bắt buộc") @Size(max = 1000, message = "Lý do tối đa 1000 ký tự") String reason,
        @Min(value = 1, message = "Số ngày gia hạn tối thiểu 1") @Max(value = 3650, message = "Số ngày gia hạn tối đa 3650") Integer extensionDays
    ) {}
    public record ResultCommand(
        @NotBlank(message = "Kết quả buổi là bắt buộc") @Size(max = 4000, message = "Kết quả tối đa 4000 ký tự") String result,
        @Size(max = 4000, message = "Hướng dẫn sau buổi tối đa 4000 ký tự") String aftercare,
        @Size(max = 4000, message = "Ghi chú nội bộ tối đa 4000 ký tự") String internalNotes,
        Boolean sharedWithCustomer
    ) {}
    public record CompleteCommand(
        @Size(max = 4000, message = "Tổng kết lộ trình tối đa 4000 ký tự") String summary
    ) {}
    public record RefundCommand(
        @NotNull(message = "Số tiền hoàn là bắt buộc") @DecimalMin(value = "0.01", message = "Số tiền hoàn phải lớn hơn 0") BigDecimal amount,
        @NotBlank(message = "Mã chứng từ hoàn tiền là bắt buộc") @Size(max = 100, message = "Mã chứng từ tối đa 100 ký tự") String reference,
        @Size(max = 250, message = "Lý do hoàn tối đa 250 ký tự") String reason
    ) {}
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
                         Map<String, Long> byStatus) {}
}
