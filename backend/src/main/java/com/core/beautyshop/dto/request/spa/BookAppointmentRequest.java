package com.core.beautyshop.dto.request.spa;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
public class BookAppointmentRequest {
    
    @NotNull(message = "Appointment date is required")
    @FutureOrPresent(message = "Appointment date must be in the future")
    private LocalDate appointmentDate;
    
    @NotNull(message = "Start time is required")
    private LocalTime startTime;
    
    private String notes;
    
    @NotEmpty(message = "At least one service must be booked")
    private List<AppointmentItemRequest> items;
}
