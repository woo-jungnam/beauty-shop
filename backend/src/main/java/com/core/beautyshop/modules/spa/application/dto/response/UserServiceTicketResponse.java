package com.core.beautyshop.modules.spa.application.dto.response;

import com.core.beautyshop.modules.spa.domain.UserServiceTicket;
import com.core.beautyshop.modules.spa.domain.enums.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Quyền gói đã mua; used/reserved/available khác bằng chứng dịch vụ đã thực hiện")
public class UserServiceTicketResponse {
    private Long id;
    private Long userId;
    private Long packageId;
    @Schema(description = "Tên lúc mua; vé cũ thiếu snapshot dùng tên hiện tại và nameSnapshot=false")
    private String packageName;
    private boolean nameSnapshot;
    private String benefitType;
    private Long treatmentCourseId;
    private java.util.List<ServiceBalance> services;
    public record ServiceBalance(Long serviceId, String serviceName, boolean nameSnapshot, int total, int used, int reserved, int available) {}
    private Long orderId;
    @Schema(description = "Tổng quyền lợi gồm bù lượt đã duyệt")
    private Integer totalSessions;
    @Schema(description = "Lượt đã REDEEMED/FORFEITED hoặc số dư legacy; không đồng nghĩa mọi lượt đã phục vụ")
    private Integer usedSessions;
    @Schema(description = "Lượt đang giữ cho các item chưa có kết quả")
    private Integer reservedSessions;
    @Schema(description = "Điều kiện hạn dùng đã chụp khi mua", allowableValues = {"BOOKING_TIME", "SERVICE_START"})
    private String expiryCheckMode;
    @Schema(description = "max(0,total-used-reserved); số này không tự chứng minh vé còn hạn/paid/ACTIVE")
    private Integer remainingSessions;
    @Schema(description = "Hạn dùng Instant UTC từ paidAt và validityDays; null là không hết hạn")
    private Instant expiryDate;
    private TicketStatus status;
    private Instant createdAt;
    @Schema(description = "Map serviceId→quota khả dụng theo đúng entitlement, đã trừ used/reserved")
    private java.util.Map<Long, Integer> remainingByService;

    public static UserServiceTicketResponse of(UserServiceTicket ticket) {
        if (ticket == null) return null;

        int remaining = Math.max(0, ticket.getTotalSessions() - (ticket.getUsedSessions() != null ? ticket.getUsedSessions() : 0) - ticket.getReservedSessions());

        return UserServiceTicketResponse.builder()
                .id(ticket.getId())
                .userId(ticket.getUserId())
                .packageId(ticket.getServicePackage() != null ? ticket.getServicePackage().getId() : null)
                .packageName(ticket.getPackageNameSnapshot() != null ? ticket.getPackageNameSnapshot() : ticket.getServicePackage() != null ? ticket.getServicePackage().getName() : null)
                .nameSnapshot(ticket.getPackageNameSnapshot() != null)
                .benefitType(ticket.getServicePackage() != null && Boolean.TRUE.equals(ticket.getServicePackage().getTreatmentManaged()) ? "TREATMENT_COURSE" : "SERVICE_PACKAGE")
                .treatmentCourseId(ticket.getTreatmentCourseId())
                .services(ticket.getEntitlements().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).map(entry -> {
                    var e = entry.getValue(); String name = ticket.getServiceNames().get(entry.getKey());
                    return new ServiceBalance(entry.getKey(), name != null ? name : "Dịch vụ #" + entry.getKey(), name != null,
                            e.getTotal(), e.getUsed(), e.getReserved(), Math.max(0, e.available()));
                }).toList())
                .orderId(ticket.getOrderId())
                .totalSessions(ticket.getTotalSessions())
                .usedSessions(ticket.getUsedSessions()).reservedSessions(ticket.getReservedSessions()).expiryCheckMode(ticket.getExpiryCheckMode())
                .remainingSessions(remaining)
                .remainingByService(ticket.getEntitlements().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        java.util.Map.Entry::getKey, entry -> Math.max(0, entry.getValue().available()))))
                .expiryDate(ticket.getExpiryDate())
                .status(ticket.getStatus())
                .createdAt(ticket.getCreatedAt())
                .build();
    }
}
