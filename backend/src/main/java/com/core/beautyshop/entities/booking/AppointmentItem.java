package com.core.beautyshop.entities.booking;

import com.core.beautyshop.entities.common.Base;
import com.core.beautyshop.entities.service.BeautyService;
import com.core.beautyshop.entities.service.Facility;
import com.core.beautyshop.entities.service.Staff;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalTime;

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
    private Staff staff; // The specific staff member assigned to perform this service

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id")
    private Facility facility; // The room or bed assigned for this service

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    private UserServiceTicket ticket; // If this service is paid via a prepaid package ticket

    @Column(name = "price", precision = 10, scale = 2)
    private BigDecimal price; // Price at the time of booking (if not using a ticket)

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;
}
