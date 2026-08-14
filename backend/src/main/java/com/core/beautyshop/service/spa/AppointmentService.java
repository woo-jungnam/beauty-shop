package com.core.beautyshop.service.spa;

import com.core.beautyshop.dto.request.spa.BookAppointmentRequest;
import com.core.beautyshop.dto.response.spa.AppointmentResponse;

import java.util.List;

public interface AppointmentService {
    AppointmentResponse bookAppointment(Long userId, BookAppointmentRequest request);
    List<AppointmentResponse> getUserAppointments(Long userId);
    AppointmentResponse getAppointmentById(Long id);
    void cancelAppointment(Long appointmentId, Long userId);
}
