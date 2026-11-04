package com.core.beautyshop.modules.notification.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "notification_receipts") @Getter @Setter @NoArgsConstructor
public class NotificationReceipt {
    @Id @Column(name = "event_id", length = 180)
    private String eventId;
    @Column(name = "processed_at", nullable = false)
    private Instant processedAt = Instant.now();
}
