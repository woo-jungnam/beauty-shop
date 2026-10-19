package com.core.beautyshop.modules.spa.domain;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
@Entity @Table(name="spa_service_refunds") @Getter @Setter @NoArgsConstructor
@org.hibernate.annotations.Immutable
public class SpaServiceRefund {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="order_id", nullable=false) private Long orderId;
    @Column(name="ticket_id", unique=true) private Long ticketId;
    @Column(name="appointment_item_id") private Long appointmentItemId;
    @Column(nullable=false, precision=19, scale=2) private BigDecimal amount;
    @Column(nullable=false, unique=true, length=100) private String reference;
    @Column(nullable=false, length=250) private String reason;
    @Column(name="actor_user_id", nullable=false) private Long actorUserId;
    @Column(name="created_at", nullable=false) private Instant createdAt = Instant.now();
}
