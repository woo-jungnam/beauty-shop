package com.core.beautyshop.modules.spa.application.dto.request;

import com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAppointmentStatusRequest {

    @NotNull(message = "Trạng thái lịch hẹn không được để trống")
    private AppointmentStatus status;

    private String notes;
}
