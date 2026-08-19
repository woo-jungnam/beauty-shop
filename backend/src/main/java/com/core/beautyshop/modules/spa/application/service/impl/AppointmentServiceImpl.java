package com.core.beautyshop.modules.spa.application.service.impl;

import com.core.beautyshop.modules.identity.api.IdentityFacade;
import com.core.beautyshop.modules.identity.api.dto.UserSummaryDto;
import com.core.beautyshop.modules.spa.application.dto.request.BookAppointmentRequest;
import com.core.beautyshop.modules.spa.application.dto.response.AppointmentResponse;
import com.core.beautyshop.modules.spa.application.service.AppointmentService;
import com.core.beautyshop.modules.spa.domain.Appointment;
import com.core.beautyshop.modules.spa.domain.AppointmentItem;
import com.core.beautyshop.modules.spa.domain.AppointmentRepository;
import com.core.beautyshop.modules.spa.domain.BeautyService;
import com.core.beautyshop.modules.spa.domain.BeautyServiceRepository;
import com.core.beautyshop.modules.spa.domain.Staff;
import com.core.beautyshop.modules.spa.domain.StaffRepository;
import com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
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
    private final IdentityFacade identityFacade;

    @Override
    @Transactional
    public AppointmentResponse bookAppointment(BookAppointmentRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        UserSummaryDto user = identityFacade.getUserSummaryById(currentUserId);

        Appointment appointment = Appointment.builder()
                .userId(currentUserId)
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
            currentStartTime = itemEndTime;
        }

        appointment.setEndTime(currentStartTime);
        appointment.setItems(items);

        Appointment saved = appointmentRepository.save(appointment);
        return AppointmentResponse.of(saved, user.getFullName());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentResponse> getMyAppointments() {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String customerName = identityFacade.findUserSummaryById(currentUserId)
                .map(UserSummaryDto::getFullName)
                .orElse(null);

        return appointmentRepository.findByUserId(currentUserId).stream()
                .map(apt -> AppointmentResponse.of(apt, customerName))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentResponse getAppointmentById(Long id) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch hẹn"));
        
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!appointment.getUserId().equals(currentUserId) && !SecurityUtils.isAdmin()) {
            throw new AccessDeniedException("Bạn không có quyền xem thông tin lịch hẹn này!");
        }

        String customerName = identityFacade.findUserSummaryById(appointment.getUserId())
                .map(UserSummaryDto::getFullName)
                .orElse(null);

        return AppointmentResponse.of(appointment, customerName);
    }

    @Override
    @Transactional
    public void cancelAppointment(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch hẹn"));
                
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!appointment.getUserId().equals(currentUserId) && !SecurityUtils.isAdmin()) {
            throw new AccessDeniedException("Bạn không có quyền hủy lịch hẹn này!");
        }
        
        if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BusinessException("Lịch hẹn đã hoàn tất, không thể hủy!");
        }
        
        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointmentRepository.save(appointment);
    }
}
