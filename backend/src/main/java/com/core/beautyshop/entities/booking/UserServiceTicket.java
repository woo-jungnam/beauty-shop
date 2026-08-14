package com.core.beautyshop.entities.booking;

import com.core.beautyshop.entities.booking.enums.TicketStatus;
import com.core.beautyshop.entities.common.Base;
import com.core.beautyshop.entities.order.Order;
import com.core.beautyshop.entities.service.ServicePackage;
import com.core.beautyshop.entities.user.User;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "package_id", nullable = false)
    private ServicePackage servicePackage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order; // Link to the order where this package was purchased

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
