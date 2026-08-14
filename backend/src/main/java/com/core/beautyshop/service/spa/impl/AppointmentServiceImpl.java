package com.core.beautyshop.service.spa.impl;

import com.core.beautyshop.dto.request.spa.BookAppointmentRequest;
import com.core.beautyshop.dto.response.spa.AppointmentResponse;
import com.core.beautyshop.entities.booking.Appointment;
import com.core.beautyshop.entities.booking.AppointmentItem;
import com.core.beautyshop.entities.booking.enums.AppointmentStatus;
import com.core.beautyshop.entities.service.BeautyService;
import com.core.beautyshop.entities.service.Staff;
import com.core.beautyshop.entities.user.User;
import com.core.beautyshop.exception.ResourceNotFoundException;
import com.core.beautyshop.repository.AppointmentRepository;
import com.core.beautyshop.repository.BeautyServiceRepository;
import com.core.beautyshop.repository.StaffRepository;
import com.core.beautyshop.repository.UserRepository;
import com.core.beautyshop.service.spa.AppointmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final BeautyServiceRepository beautyServiceRepository;
    private final StaffRepository staffRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public AppointmentResponse bookAppointment(Long userId, BookAppointmentRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        Appointment appointment = Appointment.builder()
                .user(user)
                .appointmentDate(request.getAppointmentDate())
                .startTime(request.getStartTime())
                .status(AppointmentStatus.PENDING)
                .notes(request.getNotes())
                .build();

        List<AppointmentItem> items = new ArrayList<>();
        LocalTime currentStartTime = request.getStartTime();

        for (var itemReq : request.getItems()) {
            BeautyService service = beautyServiceRepository.findById(itemReq.getServiceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dịch vụ"));
            
            Staff staff = null;
            if (itemReq.getStaffId() != null) {
                staff = staffRepository.findById(itemReq.getStaffId())
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên"));
            }
            
            int totalDuration = service.getDurationMinutes() + service.getPreparationTimeMinutes();
            LocalTime itemEndTime = currentStartTime.plusMinutes(totalDuration);

            AppointmentItem item = AppointmentItem.builder()
                    .appointment(appointment)
                    .service(service)
                    .staff(staff)
                    .price(service.getBasePrice())
                    .startTime(currentStartTime)
                    .endTime(itemEndTime)
                    .build();
            
            items.add(item);
            currentStartTime = itemEndTime; // next service starts after current one ends
        }

        appointment.setEndTime(currentStartTime); // total end time
        appointment.setItems(items);

        Appointment saved = appointmentRepository.save(appointment);
        return AppointmentResponse.fromEntity(saved);
    }

    @Override
    public List<AppointmentResponse> getUserAppointments(Long userId) {
        return appointmentRepository.findByUserId(userId).stream()
                .map(AppointmentResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public AppointmentResponse getAppointmentById(Long id) {
        return appointmentRepository.findById(id)
                .map(AppointmentResponse::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch hẹn"));
    }

    @Override
    @Transactional
    public void cancelAppointment(Long appointmentId, Long userId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch hẹn"));
                
        if (!appointment.getUser().getId().equals(userId)) {
            throw new RuntimeException("Không có quyền hủy lịch hẹn này");
        }
        
        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointmentRepository.save(appointment);
    }
}
