package com.core.beautyshop.modules.spa.domain;

import com.core.beautyshop.modules.spa.domain.enums.TicketStatus;
import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "user_service_tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserServiceTicket extends Base {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "package_id", nullable = false)
    private ServicePackage servicePackage;

    @Column(name = "order_id")
    private Long orderId; // Link to the order where this package was purchased

    @Column(name = "total_sessions", nullable = false)
    private Integer totalSessions;

    @Column(name = "used_sessions", nullable = false)
    @Builder.Default
    private Integer usedSessions = 0;

    @Column(name = "expiry_date")
    private Instant expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    @Builder.Default
    private TicketStatus status = TicketStatus.ACTIVE;
}
