package com.core.beautyshop.dto.response.spa;

import com.core.beautyshop.entities.booking.Appointment;
import com.core.beautyshop.entities.booking.enums.AppointmentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
public class AppointmentResponse {
    private Long id;
    private Long userId;
    private String customerName;
    private LocalDate appointmentDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private AppointmentStatus status;
    private String notes;
    private List<AppointmentItemResponse> items;

    public static AppointmentResponse fromEntity(Appointment entity) {
        if (entity == null) return null;
        
        List<AppointmentItemResponse> itemResponses = entity.getItems() != null ?
                entity.getItems().stream()
                        .map(item -> AppointmentItemResponse.builder()
                                .serviceName(item.getService().getName())
                                .staffName(item.getStaff() != null && item.getStaff().getUser() != null ? item.getStaff().getUser().getFullName() : "No preference")
                                .price(item.getPrice())
                                .startTime(item.getStartTime())
                                .endTime(item.getEndTime())
                                .build())
                        .collect(Collectors.toList()) : List.of();

        return AppointmentResponse.builder()
                .id(entity.getId())
                .userId(entity.getUser() != null ? entity.getUser().getId() : null)
                .customerName(entity.getUser() != null ? entity.getUser().getFullName() : null)
                .appointmentDate(entity.getAppointmentDate())
                .startTime(entity.getStartTime())
                .endTime(entity.getEndTime())
                .status(entity.getStatus())
                .notes(entity.getNotes())
                .items(itemResponses)
                .build();
    }
}
