package com.core.beautyshop.modules.spa.application.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AppointmentItemRequest {
    
    @NotNull(message = "Service ID is required")
    private Long serviceId;
    
    // Optional: User can choose a specific staff
    private Long staffId;
    
    // Optional: if the user uses a prepaid ticket
    private Long ticketId;
}
