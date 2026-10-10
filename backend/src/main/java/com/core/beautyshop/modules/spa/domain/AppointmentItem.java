package com.core.beautyshop.modules.spa.domain;

import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus;
import com.core.beautyshop.modules.spa.domain.enums.TicketUsageState;

@Entity
@Table(name = "appointment_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppointmentItem extends Base {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private BeautyService service;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private Staff staff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id")
    private Facility facility;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    private UserServiceTicket ticket;

    @Column(name = "price", precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "service_name_snapshot", length = 150)
    private String serviceNameSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_status", nullable = false, length = 30)
    @Builder.Default
    private AppointmentItemExecutionStatus executionStatus = AppointmentItemExecutionStatus.PLANNED;

    @Enumerated(EnumType.STRING)
    @Column(name = "ticket_usage_state", nullable = false, length = 30)
    @Builder.Default
    private TicketUsageState ticketUsageState = TicketUsageState.NONE;

    @Column(name = "actual_started_at")
    private Instant actualStartedAt;
    @Column(name = "actual_completed_at")
    private Instant actualCompletedAt;
    @Column(name = "performed_by_user_id")
    private Long performedByUserId;
    @Column(name = "execution_notes", length = 2000)
    private String executionNotes;

    @OneToMany(mappedBy = "appointmentItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<FacilityAllocation> facilityAllocations = new ArrayList<>();

    @OneToMany(mappedBy = "appointmentItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AppointmentItemResourceRequirement> resourceRequirements = new ArrayList<>();
}
