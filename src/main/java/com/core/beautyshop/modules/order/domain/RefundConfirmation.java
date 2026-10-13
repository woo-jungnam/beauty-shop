package com.core.beautyshop.modules.order.domain;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name = "refund_confirmations") @Getter @Setter @NoArgsConstructor
public class RefundConfirmation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "order_id", nullable = false) private Long orderId;
    @Column(name = "bank_reference", nullable = false, unique = true, length = 100) private String reference;
    @Column(nullable = false, precision = 12, scale = 2) private java.math.BigDecimal amount;
    @Column(name = "confirmed_at", nullable = false) private java.time.Instant confirmedAt = java.time.Instant.now();
    @Column(name = "confirmed_by_user_id") private Long confirmedByUserId;
    @Column(name = "approval_reason", length = 250) private String approvalReason;
}
