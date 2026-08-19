package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.spa.application.dto.request.BookAppointmentRequest;
import com.core.beautyshop.modules.spa.application.dto.response.AppointmentResponse;

import java.util.List;

public interface AppointmentService {
    AppointmentResponse bookAppointment(BookAppointmentRequest request);
    List<AppointmentResponse> getMyAppointments();
    AppointmentResponse getAppointmentById(Long id);
    void cancelAppointment(Long appointmentId);
}

