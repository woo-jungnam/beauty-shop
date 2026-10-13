package com.core.beautyshop.modules.notification.domain;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name = "notification_receipts") @Getter @Setter @NoArgsConstructor
public class NotificationReceipt {
    @Id @Column(name = "event_id", length = 180)
    private String eventId;
    @Column(name = "processed_at", nullable = false)
    private java.time.Instant processedAt = java.time.Instant.now();
}
