package com.core.beautyshop.modules.spa.application.dto.response;

import com.core.beautyshop.modules.spa.domain.Appointment;
import com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Lịch hẹn và bằng chứng thực hiện; COMPLETED không đồng nghĩa đã trả tiền")
public class AppointmentResponse {
    private Long id;
    private Long userId;
    private String customerName;
    private String customerPhone;
    @Schema(description = "Order invoice buổi lẻ nếu đã lập; null nếu chưa invoice")
    private Long orderId;
    @Schema(description = "Ngày dự kiến tại Việt Nam", type = "string", format = "date")
    private LocalDate appointmentDate;
    @Schema(description = "Giờ dự kiến bắt đầu buổi tại Việt Nam", type = "string", format = "time", example = "10:00:00")
    private LocalTime startTime;
    @Schema(description = "Giờ dự kiến kết thúc, gồm chuẩn bị", type = "string", format = "time", example = "11:00:00")
    private LocalTime endTime;
    private AppointmentStatus status;
    private String notes;
    @Schema(description = "Thời điểm khách đến đã ghi nhận; Instant UTC, có thể null")
    private java.time.Instant checkedInAt;
    private Long checkedInByUserId;
    @Schema(description = "Thời điểm bắt đầu buổi thực tế; không thay actual của từng item")
    private java.time.Instant actualStartedAt;
    @Schema(description = "Thời điểm hoàn tất buổi thực tế; legacy có thể null")
    private java.time.Instant actualCompletedAt;
    @Schema(description = "Hạn PENDING nếu TTL đã bật; null khi TTL=0 hoặc lịch không còn PENDING")
    private java.time.Instant pendingExpiresAt;
    @Schema(description = "Chuỗi JSON của policy đã chụp lúc đặt, không phải object JSON; null với legacy booking")
    private String policySnapshot;

    private List<AppointmentItemResponse> items;

    public static AppointmentResponse of(Appointment entity, String customerName) {
        return of(entity, customerName, null, java.util.Map.of());
    }

    public static AppointmentResponse of(Appointment entity, String customerName, String customerPhone) {
        return of(entity, customerName, customerPhone, java.util.Map.of());
    }

    public static AppointmentResponse of(Appointment entity, String customerName, String customerPhone, java.util.Map<Long, String> staffNames) {
        if (entity == null) return null;

        List<AppointmentItemResponse> itemResponses = entity.getItems() != null ?
                entity.getItems().stream()
                        .map(item -> {
                            String resolvedStaffName = "No preference";
                            if (item.getStaff() != null) {
                                String customName = staffNames != null ? staffNames.get(item.getStaff().getId()) : null;
                                resolvedStaffName = (customName != null && !customName.isBlank()) ? customName : "Staff #" + item.getStaff().getId();
                            }

                            return AppointmentItemResponse.builder()
                                    .id(item.getId())
                                    .serviceId(item.getService() != null ? item.getService().getId() : null)
                                    .staffId(item.getStaff() != null ? item.getStaff().getId() : null)
                                    .serviceName(item.getServiceNameSnapshot() != null ? item.getServiceNameSnapshot() : item.getService() != null ? item.getService().getName() : null)
                                    .staffName(resolvedStaffName)
                                    .ticketId(item.getTicket() != null ? item.getTicket().getId() : null)
                                    .isTicketUsed(item.getTicket() != null)
                                    .price(item.getPrice())
                                    .startTime(item.getStartTime())
                                    .endTime(item.getEndTime())
                                    .executionStatus(item.getExecutionStatus()).ticketUsageState(item.getTicketUsageState())
                                    .actualStartedAt(item.getActualStartedAt()).actualCompletedAt(item.getActualCompletedAt())
                                    .performedByUserId(item.getPerformedByUserId()).executionNotes(item.getExecutionNotes())
                                    .facilityId(item.getFacility() == null ? null : item.getFacility().getId())
                                    .resources(item.getFacilityAllocations().stream().map(a -> new AppointmentItemResponse.ResourceAllocation(
                                        a.getFacility().getId(), a.getFacility().getName(), a.getFacility().getType(), a.getQuantity())).toList())
                                    .build();
                        })
                        .collect(Collectors.toList()) : List.of();

        return AppointmentResponse.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .customerName(customerName)
                .customerPhone(customerPhone)
                .orderId(entity.getOrderId())
                .appointmentDate(entity.getAppointmentDate())
                .startTime(entity.getStartTime())
                .endTime(entity.getEndTime())
                .status(entity.getStatus())
                .notes(entity.getNotes()).checkedInAt(entity.getCheckedInAt()).checkedInByUserId(entity.getCheckedInByUserId())
                .actualStartedAt(entity.getActualStartedAt()).actualCompletedAt(entity.getActualCompletedAt())
                .pendingExpiresAt(entity.getPendingExpiresAt()).policySnapshot(entity.getPolicySnapshot())
                .items(itemResponses)
                .build();
    }
}
