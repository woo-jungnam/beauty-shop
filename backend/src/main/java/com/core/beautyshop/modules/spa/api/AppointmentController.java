package com.core.beautyshop.modules.spa.api;

import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.modules.spa.application.dto.request.BookAppointmentRequest;
import com.core.beautyshop.modules.spa.application.dto.response.AppointmentResponse;
import com.core.beautyshop.modules.spa.application.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Lịch hẹn Spa", description = "API quản lý đặt lịch Dịch vụ Spa")
@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    @Operation(summary = "Đặt lịch hẹn Spa mới")
    @PostMapping("/book")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AppointmentResponse>> bookAppointment(
            @Valid @RequestBody BookAppointmentRequest request) {
        AppointmentResponse response = appointmentService.bookAppointment(request);
        return ResponseEntity.status(201).body(ApiResponse.created(
                response,
                "Đặt lịch hẹn thành công"
        ));
    }

    @Operation(summary = "Xem danh sách lịch hẹn của tôi")
    @GetMapping("/my-appointments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getUserAppointments() {
        return ResponseEntity.ok(ApiResponse.success(
                appointmentService.getMyAppointments()
        ));
    }

    @Operation(summary = "Xem chi tiết một lịch hẹn")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AppointmentResponse>> getAppointmentById(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                appointmentService.getAppointmentById(id)
        ));
    }

    @Operation(summary = "Hủy lịch hẹn")
    @PutMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> cancelAppointment(
            @PathVariable Long id) {
        appointmentService.cancelAppointment(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Xem toàn bộ danh sách lịch hẹn Spa (Admin / Staff)")
    @GetMapping("/admin/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<com.core.beautyshop.shared.dto.PageResponse<AppointmentResponse>>> getAllAppointments(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date,
            @RequestParam(required = false) com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus status,
            @org.springdoc.core.annotations.ParameterObject @org.springframework.data.web.PageableDefault(size = 20) org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Page<AppointmentResponse> page = appointmentService.getAllAppointments(date, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(com.core.beautyshop.shared.dto.PageResponse.of(page)));
    }

    @Operation(summary = "Cập nhật trạng thái lịch hẹn Spa (Admin / Staff)")
    @PutMapping("/admin/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> updateAppointmentStatus(
            @PathVariable Long id,
            @Valid @RequestBody com.core.beautyshop.modules.spa.application.dto.request.UpdateAppointmentStatusRequest request) {
        AppointmentResponse response = appointmentService.updateAppointmentStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Điều chỉnh lịch hẹn (Admin / Staff / Customer)")
    @PutMapping("/{id}/reschedule")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AppointmentResponse>> rescheduleAppointment(
            @PathVariable Long id,
            @Valid @RequestBody com.core.beautyshop.modules.spa.application.dto.request.RescheduleAppointmentRequest request) {
        AppointmentResponse response = appointmentService.rescheduleAppointment(id, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

