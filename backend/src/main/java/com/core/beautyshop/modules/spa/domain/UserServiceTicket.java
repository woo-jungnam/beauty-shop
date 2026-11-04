package com.core.beautyshop.modules.spa.domain;

import com.core.beautyshop.modules.spa.domain.enums.TicketStatus;
import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

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

    @Column(name = "order_id", unique = true)
    private Long orderId;

    @Column(name = "package_name_snapshot", length = 255)
    private String packageNameSnapshot;

    @Column(name = "treatment_course_id")
    private Long treatmentCourseId;

    @ElementCollection
    @CollectionTable(name = "ticket_service_names", joinColumns = @JoinColumn(name = "ticket_id"))
    @MapKeyColumn(name = "service_id") @Column(name = "service_name", nullable = false, length = 255)
    @Builder.Default
    private Map<Long, String> serviceNames = new HashMap<>();

    @Column(name = "total_sessions", nullable = false)
    private Integer totalSessions;

    @Column(name = "used_sessions", nullable = false)
    @Builder.Default
    private Integer usedSessions = 0;

    @Column(name = "reserved_sessions", nullable = false)
    @Builder.Default
    private Integer reservedSessions = 0;

    @Column(name = "expiry_check_mode", nullable = false, length = 30)
    @Builder.Default
    private String expiryCheckMode = "BOOKING_TIME";

    @ElementCollection
    @BatchSize(size = 50)
    @CollectionTable(name = "ticket_entitlements", joinColumns = @JoinColumn(name = "ticket_id"))
    @MapKeyColumn(name = "service_id")
    @Builder.Default
    private Map<Long, TicketEntitlement> entitlements = new HashMap<>();

    @Column(name = "expiry_date")
    private Instant expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    @Builder.Default
    private TicketStatus status = TicketStatus.ACTIVE;
}
