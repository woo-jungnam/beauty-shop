package com.core.beautyshop.modules.spa.application.dto.request;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BookAppointmentRequest {
    
    @NotNull(message = "Ngày hẹn không được để trống")
    @FutureOrPresent(message = "Ngày hẹn phải từ hôm nay trở đi")
    private LocalDate appointmentDate;
    
    @NotNull(message = "Giờ bắt đầu không được để trống")
    private LocalTime startTime;
    
    private String notes;
    
    @NotEmpty(message = "Cần chọn ít nhất một dịch vụ để đặt lịch")
    private List<@Valid AppointmentItemRequest> items;
}
