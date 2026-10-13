package com.core.beautyshop.modules.spa.domain;

import com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus;
import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Entity
@Table(name = "appointments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Appointment extends Base {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    @Builder.Default
    private AppointmentStatus status = AppointmentStatus.PENDING;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "checked_in_at")
    private java.time.Instant checkedInAt;
    @Column(name = "checked_in_by_user_id")
    private Long checkedInByUserId;
    @Column(name = "actual_started_at")
    private java.time.Instant actualStartedAt;
    @Column(name = "actual_completed_at")
    private java.time.Instant actualCompletedAt;
    @Column(name = "pending_expires_at")
    private java.time.Instant pendingExpiresAt;
    @Column(name = "policy_snapshot", columnDefinition = "text")
    private String policySnapshot;

    @OneToMany(mappedBy = "appointment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startTime ASC, id ASC")
    @Builder.Default
    private List<AppointmentItem> items = new java.util.ArrayList<>();
}
