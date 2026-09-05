package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.spa.application.dto.request.BookAppointmentRequest;
import com.core.beautyshop.modules.spa.application.dto.request.UpdateAppointmentStatusRequest;
import com.core.beautyshop.modules.spa.application.dto.response.AppointmentResponse;
import com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface AppointmentService {
    AppointmentResponse bookAppointment(BookAppointmentRequest request);
    List<AppointmentResponse> getMyAppointments();
    AppointmentResponse getAppointmentById(Long id);
    void cancelAppointment(Long appointmentId);
    Page<AppointmentResponse> getAllAppointments(LocalDate date, AppointmentStatus status, Pageable pageable);
    AppointmentResponse updateAppointmentStatus(Long id, UpdateAppointmentStatusRequest request);
    AppointmentResponse rescheduleAppointment(Long id, com.core.beautyshop.modules.spa.application.dto.request.RescheduleAppointmentRequest request);
}

