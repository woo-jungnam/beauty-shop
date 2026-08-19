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
    private Long orderId; // Link to the order if they paid for this session

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

    @OneToMany(mappedBy = "appointment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AppointmentItem> items;
}
