package com.core.beautyshop.modules.spa.domain.treatment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "treatment_notifications", uniqueConstraints = @UniqueConstraint(columnNames = "event_key"))
@Getter @Setter
public class TreatmentNotification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "course_id", nullable = false) private Long courseId;
    @Column(name = "event_key", nullable = false, length = 150) private String eventKey;
    @Column(nullable = false, length = 60) private String kind;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, length = 1000) private String message;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    @Column(name = "read_at") private Instant readAt;
}
