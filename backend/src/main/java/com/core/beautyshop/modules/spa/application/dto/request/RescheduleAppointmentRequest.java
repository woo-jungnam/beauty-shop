package com.core.beautyshop.modules.spa.application.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class RescheduleAppointmentRequest {
    @NotNull(message = "Ngày hẹn không được để trống")
    private LocalDate appointmentDate;

    @NotNull(message = "Giờ hẹn không được để trống")
    private LocalTime startTime;

    private String notes;
}
