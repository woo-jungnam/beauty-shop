package com.core.beautyshop.modules.spa.domain;

import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.*;
import lombok.*;

@org.hibernate.annotations.Immutable
@Entity @Table(name = "ticket_session_movements") @Getter @Setter @NoArgsConstructor
public class TicketSessionMovement extends Base {
    @Column(name = "ticket_id", nullable = false, updatable = false) private Long ticketId;
    @Column(name = "service_id", updatable = false) private Long serviceId;
    @Column(name = "appointment_item_id", updatable = false) private Long appointmentItemId;
    @Column(nullable = false, length = 30, updatable = false) private String operation;
    @Column(name = "used_after", nullable = false, updatable = false) private int usedAfter;
    @Column(name = "reserved_after", nullable = false, updatable = false) private int reservedAfter;
    @Column(name = "available_after", nullable = false, updatable = false) private int availableAfter;
    @Column(name = "actor_user_id", updatable = false) private Long actorUserId;
    @Column(length = 2000, updatable = false) private String reason;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 150, updatable = false)
    private String idempotencyKey;
    @Column(name="command_fingerprint", length=64, updatable=false) private String commandFingerprint;
    @Column(name="session_adjustment", updatable=false) private Integer sessionAdjustment;
    @Column(name="expiry_before", updatable=false) private java.time.Instant expiryBefore;
    @Column(name="expiry_after", updatable=false) private java.time.Instant expiryAfter;
}
