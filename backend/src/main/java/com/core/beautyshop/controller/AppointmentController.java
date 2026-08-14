package com.core.beautyshop.controller;

import com.core.beautyshop.dto.common.ApiResponse;
import com.core.beautyshop.dto.request.spa.BookAppointmentRequest;
import com.core.beautyshop.dto.response.spa.AppointmentResponse;
import com.core.beautyshop.service.spa.AppointmentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Lịch hẹn Spa", description = "API người dùng để đặt Dịch vụ Spa")
@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    @PostMapping("/book")
    public ResponseEntity<ApiResponse<AppointmentResponse>> bookAppointment(
            @RequestParam Long userId,
            @Valid @RequestBody BookAppointmentRequest request,
            HttpServletRequest req) {
        // Note: In real app, userId should come from SecurityContext
        return ResponseEntity.ok(ApiResponse.created(
                appointmentService.bookAppointment(userId, request),
                "Appointment booked successfully",
                req.getRequestURI()
        ));
    }

    @GetMapping("/my-appointments")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getUserAppointments(
            @RequestParam Long userId,
            HttpServletRequest req) {
        return ResponseEntity.ok(ApiResponse.success(
                appointmentService.getUserAppointments(userId), req.getRequestURI()
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AppointmentResponse>> getAppointmentById(
            @PathVariable Long id, HttpServletRequest req) {
        return ResponseEntity.ok(ApiResponse.success(
                appointmentService.getAppointmentById(id), req.getRequestURI()
        ));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelAppointment(
            @PathVariable Long id,
            @RequestParam Long userId,
            HttpServletRequest req) {
        appointmentService.cancelAppointment(id, userId);
        return ResponseEntity.ok(ApiResponse.success(null, req.getRequestURI()));
    }
}
