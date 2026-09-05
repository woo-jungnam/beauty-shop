package com.core.beautyshop.modules.spa.application.dto.response;

import com.core.beautyshop.modules.spa.domain.Appointment;
import com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentResponse {
    private Long id;
    private Long userId;
    private String customerName;
    private Long orderId;
    private LocalDate appointmentDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private AppointmentStatus status;
    private String notes;
    private List<AppointmentItemResponse> items;

    public static AppointmentResponse of(Appointment entity, String customerName) {
        if (entity == null) return null;
        
        List<AppointmentItemResponse> itemResponses = entity.getItems() != null ?
                entity.getItems().stream()
                        .map(item -> AppointmentItemResponse.builder()
                                .serviceName(item.getService() != null ? item.getService().getName() : null)
                                .staffName(item.getStaff() != null ? "Staff #" + item.getStaff().getId() : "No preference")
                                .ticketId(item.getTicket() != null ? item.getTicket().getId() : null)
                                .isTicketUsed(item.getTicket() != null)
                                .price(item.getPrice())
                                .startTime(item.getStartTime())
                                .endTime(item.getEndTime())
                                .build())
                        .collect(Collectors.toList()) : List.of();

        return AppointmentResponse.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .customerName(customerName)
                .orderId(entity.getOrderId())
                .appointmentDate(entity.getAppointmentDate())
                .startTime(entity.getStartTime())
                .endTime(entity.getEndTime())
                .status(entity.getStatus())
                .notes(entity.getNotes())
                .items(itemResponses)
                .build();
    }
}
