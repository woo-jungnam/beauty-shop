package com.core.beautyshop.modules.spa.application.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class RescheduleAppointmentRequest {
    @NotNull(message = "Ngày hẹn không được để trống")
    @jakarta.validation.constraints.FutureOrPresent(message = "Ngày hẹn phải từ hôm nay trở đi")
    private LocalDate appointmentDate;

    @NotNull(message = "Giờ hẹn không được để trống")
    private LocalTime startTime;

    @Size(max = 500)
    private String notes;
}
