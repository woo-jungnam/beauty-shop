package com.core.beautyshop.modules.spa.application.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AppointmentItemRequest {

    @NotNull(message = "Service ID is required")
    private Long serviceId;

    private Long staffId;

    private Long ticketId;
}
