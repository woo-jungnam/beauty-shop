package com.core.beautyshop.modules.spa.application.dto.response;

import com.core.beautyshop.modules.spa.domain.UserServiceTicket;
import com.core.beautyshop.modules.spa.domain.enums.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserServiceTicketResponse {
    private Long id;
    private Long userId;
    private Long packageId;
    private String packageName;
    private Long orderId;
    private Integer totalSessions;
    private Integer usedSessions;
    private Integer remainingSessions;
    private Instant expiryDate;
    private TicketStatus status;
    private Instant createdAt;

    public static UserServiceTicketResponse of(UserServiceTicket ticket) {
        if (ticket == null) return null;

        int remaining = Math.max(0, ticket.getTotalSessions() - (ticket.getUsedSessions() != null ? ticket.getUsedSessions() : 0));

        return UserServiceTicketResponse.builder()
                .id(ticket.getId())
                .userId(ticket.getUserId())
                .packageId(ticket.getServicePackage() != null ? ticket.getServicePackage().getId() : null)
                .packageName(ticket.getServicePackage() != null ? ticket.getServicePackage().getName() : null)
                .orderId(ticket.getOrderId())
                .totalSessions(ticket.getTotalSessions())
                .usedSessions(ticket.getUsedSessions())
                .remainingSessions(remaining)
                .expiryDate(ticket.getExpiryDate())
                .status(ticket.getStatus())
                .createdAt(ticket.getCreatedAt())
                .build();
    }
}
