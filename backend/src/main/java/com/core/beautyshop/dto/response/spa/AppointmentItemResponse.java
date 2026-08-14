package com.core.beautyshop.dto.response.spa;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalTime;

@Data
@Builder
public class AppointmentItemResponse {
    private String serviceName;
    private String staffName;
    private BigDecimal price;
    private LocalTime startTime;
    private LocalTime endTime;
}
