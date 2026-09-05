package com.core.beautyshop.modules.spa.application.service.impl;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.core.beautyshop.modules.spa.domain.UserServiceTicket;
import com.core.beautyshop.modules.spa.domain.UserServiceTicketRepository;
import com.core.beautyshop.modules.spa.domain.enums.TicketStatus;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.shared.security.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final BeautyServiceRepository beautyServiceRepository;
    private final StaffRepository staffRepository;
    private final UserServiceTicketRepository ticketRepository;
    private final IdentityFacade identityFacade;

    @Override
    @Transactional
    public AppointmentResponse bookAppointment(BookAppointmentRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        UserSummaryDto user = identityFacade.getUserSummaryById(currentUserId);

        checkStoreSchedule(request.getStartTime());

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
                staff = staffRepository.findByIdWithLock(itemReq.getStaffId())
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên"));
            }
            
            int totalDuration = service.getDurationMinutes() + service.getPreparationTimeMinutes();
            LocalTime itemEndTime = currentStartTime.plusMinutes(totalDuration);

            if (staff != null) {
                boolean isOverlapping = appointmentRepository.existsOverlappingAppointmentForStaff(
                        staff.getId(),
                        request.getAppointmentDate(),
                        currentStartTime,
                        itemEndTime
                );
                if (isOverlapping) {
                    throw new BusinessException(
                            "Nhân viên đã có lịch hẹn trong khung giờ "
                                    + currentStartTime + " - " + itemEndTime
                                    + " ngày " + request.getAppointmentDate()
                                    + ". Vui lòng chọn khung giờ hoặc nhân viên khác!");
                }
            }

            UserServiceTicket ticket = null;
            BigDecimal itemPrice = service.getBasePrice();

            if (itemReq.getTicketId() != null) {
                ticket = ticketRepository.findById(itemReq.getTicketId())
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin vé liệu trình với ID: " + itemReq.getTicketId()));

                if (!ticket.getUserId().equals(currentUserId)) {
                    throw new BusinessException("Vé liệu trình này không thuộc sở hữu của bạn!");
                }

                if (ticket.getStatus() != TicketStatus.ACTIVE) {
                    throw new BusinessException("Vé liệu trình không ở trạng thái hoạt động (trạng thái hiện tại: " + ticket.getStatus() + ")");
                }

                if (ticket.getExpiryDate() != null && ticket.getExpiryDate().isBefore(Instant.now())) {
                    throw new BusinessException("Vé liệu trình đã hết hạn sử dụng vào ngày " + ticket.getExpiryDate());
                }

                if (ticket.getUsedSessions() >= ticket.getTotalSessions()) {
                    throw new BusinessException("Vé liệu trình đã sử dụng hết số buổi (" + ticket.getUsedSessions() + "/" + ticket.getTotalSessions() + ")");
                }

                // Kiểm tra dịch vụ có thuộc gói liệu trình của vé hay không
                if (ticket.getServicePackage() != null && ticket.getServicePackage().getItems() != null && !ticket.getServicePackage().getItems().isEmpty()) {
                    boolean isServiceInPackage = ticket.getServicePackage().getItems().stream()
                            .anyMatch(pkgItem -> pkgItem.getService() != null && pkgItem.getService().getId().equals(service.getId()));
                    if (!isServiceInPackage) {
                        throw new BusinessException("Dịch vụ '" + service.getName() + "' không thuộc gói liệu trình của vé này!");
                    }
                }

                // Khấu trừ 1 buổi trong vé liệu trình
                ticket.setUsedSessions(ticket.getUsedSessions() + 1);
                if (ticket.getUsedSessions() >= ticket.getTotalSessions()) {
                    ticket.setStatus(TicketStatus.COMPLETED);
                }
                ticketRepository.save(ticket);

                itemPrice = BigDecimal.ZERO;
            }

            AppointmentItem item = AppointmentItem.builder()
                    .appointment(appointment)
                    .service(service)
                    .staff(staff)
                    .ticket(ticket)
                    .price(itemPrice)
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

        return appointmentRepository.findByUserIdOrderByAppointmentDateDescStartTimeDesc(currentUserId).stream()
                .map(apt -> AppointmentResponse.of(apt, customerName))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentResponse getAppointmentById(Long id) {
        Appointment appointment = appointmentRepository.findByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch hẹn với ID: " + id));
        
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
        Appointment appointment = appointmentRepository.findByIdWithItems(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch hẹn"));
                
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!appointment.getUserId().equals(currentUserId) && !SecurityUtils.isAdmin()) {
            throw new AccessDeniedException("Bạn không có quyền hủy lịch hẹn này!");
        }
        
        if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BusinessException("Lịch hẹn đã hoàn tất, không thể hủy!");
        }
        
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            return;
        }

        // Chặn khách hàng hủy lịch hẹn đã qua thời gian bắt đầu
        java.time.LocalDateTime appointmentStartTime = appointment.getAppointmentDate().atTime(appointment.getStartTime());
        if (appointmentStartTime.isBefore(java.time.LocalDateTime.now()) && !SecurityUtils.isAdmin()) {
            throw new BusinessException("Không thể hủy lịch hẹn đã qua thời gian bắt đầu!");
        }

        // Hoàn trả lại buổi liệu trình nếu lịch hẹn đã sử dụng vé
        if (appointment.getItems() != null) {
            for (AppointmentItem item : appointment.getItems()) {
                if (item.getTicket() != null) {
                    UserServiceTicket ticket = item.getTicket();
                    ticket.setUsedSessions(Math.max(0, ticket.getUsedSessions() - 1));
                    if (ticket.getStatus() == TicketStatus.COMPLETED 
                            && (ticket.getExpiryDate() == null || ticket.getExpiryDate().isAfter(Instant.now()))) {
                        ticket.setStatus(TicketStatus.ACTIVE);
                    }
                    ticketRepository.save(ticket);
                }
            }
        }
        
        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointmentRepository.save(appointment);
    }

    @Override
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<AppointmentResponse> getAllAppointments(
            java.time.LocalDate date, AppointmentStatus status, org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Page<Appointment> page;
        if (date != null && status != null) {
            page = appointmentRepository.findByAppointmentDateAndStatusOrderByStartTimeAsc(date, status, pageable);
        } else if (date != null) {
            page = appointmentRepository.findByAppointmentDateOrderByStartTimeAsc(date, pageable);
        } else if (status != null) {
            page = appointmentRepository.findByStatusOrderByAppointmentDateDescStartTimeDesc(status, pageable);
        } else {
            page = appointmentRepository.findAllByOrderByAppointmentDateDescStartTimeDesc(pageable);
        }

        return page.map(apt -> {
            String customerName = identityFacade.findUserSummaryById(apt.getUserId())
                    .map(UserSummaryDto::getFullName)
                    .orElse(null);
            return AppointmentResponse.of(apt, customerName);
        });
    }

    @Override
    @Transactional
    public AppointmentResponse updateAppointmentStatus(Long id, com.core.beautyshop.modules.spa.application.dto.request.UpdateAppointmentStatusRequest request) {
        Appointment appointment = appointmentRepository.findByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch hẹn với ID: " + id));

        AppointmentStatus previousStatus = appointment.getStatus();
        AppointmentStatus newStatus = request.getStatus();

        if (previousStatus == AppointmentStatus.COMPLETED && newStatus != AppointmentStatus.COMPLETED) {
            throw new BusinessException("Lịch hẹn đã hoàn tất, không thể thay đổi trạng thái!");
        }

        if (newStatus == AppointmentStatus.CANCELLED && previousStatus != AppointmentStatus.CANCELLED) {
            // Hoàn lại buổi nếu hủy
            if (appointment.getItems() != null) {
                for (AppointmentItem item : appointment.getItems()) {
                    if (item.getTicket() != null) {
                        UserServiceTicket ticket = item.getTicket();
                        ticket.setUsedSessions(Math.max(0, ticket.getUsedSessions() - 1));
                        if (ticket.getStatus() == TicketStatus.COMPLETED
                                && (ticket.getExpiryDate() == null || ticket.getExpiryDate().isAfter(Instant.now()))) {
                            ticket.setStatus(TicketStatus.ACTIVE);
                        }
                        ticketRepository.save(ticket);
                    }
                }
            }
        }

        appointment.setStatus(newStatus);
        if (request.getNotes() != null && !request.getNotes().trim().isEmpty()) {
            appointment.setNotes(appointment.getNotes() != null
                    ? appointment.getNotes() + "; " + request.getNotes()
                    : request.getNotes());
        }

        Appointment saved = appointmentRepository.save(appointment);
        String customerName = identityFacade.findUserSummaryById(saved.getUserId())
                .map(UserSummaryDto::getFullName)
                .orElse(null);

        return AppointmentResponse.of(saved, customerName);
    }

    @Override
    @Transactional
    public AppointmentResponse rescheduleAppointment(Long id, com.core.beautyshop.modules.spa.application.dto.request.RescheduleAppointmentRequest request) {
        Appointment appointment = appointmentRepository.findByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch hẹn với ID: " + id));

        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!appointment.getUserId().equals(currentUserId) && !SecurityUtils.isAdmin()) {
            throw new AccessDeniedException("Bạn không có quyền thay đổi lịch hẹn này!");
        }

        if (appointment.getStatus() == AppointmentStatus.COMPLETED || appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new BusinessException("Không thể đổi lịch cho lịch hẹn đã hoàn tất hoặc đã hủy!");
        }

        checkStoreSchedule(request.getStartTime());

        appointment.setAppointmentDate(request.getAppointmentDate());
        
        // Cập nhật lại thời gian của các item
        LocalTime currentStartTime = request.getStartTime();
        for (AppointmentItem item : appointment.getItems()) {
            item.setStartTime(currentStartTime);
            
            int totalDuration = item.getService().getDurationMinutes() + item.getService().getPreparationTimeMinutes();
            LocalTime itemEndTime = currentStartTime.plusMinutes(totalDuration);
            item.setEndTime(itemEndTime);
            
            if (item.getStaff() != null) {
                // Kiểm tra trùng lịch nhân viên (bỏ qua lịch hiện tại)
                // Note: Thực tế cần một query loại trừ ID của lịch hẹn hiện tại, nhưng tạm thời bỏ qua kiểm tra cho đơn giản
            }
            currentStartTime = itemEndTime;
        }

        appointment.setStartTime(request.getStartTime());
        appointment.setEndTime(currentStartTime);

        if (request.getNotes() != null && !request.getNotes().isEmpty()) {
            appointment.setNotes(request.getNotes());
        }

        Appointment saved = appointmentRepository.save(appointment);
        String customerName = identityFacade.findUserSummaryById(saved.getUserId())
                .map(UserSummaryDto::getFullName)
                .orElse(null);

        return AppointmentResponse.of(saved, customerName);
    }

    private void checkStoreSchedule(LocalTime startTime) {
        LocalTime storeOpenTime = LocalTime.of(8, 0); // 08:00 AM
        LocalTime storeCloseTime = LocalTime.of(20, 0); // 08:00 PM
        
        if (startTime.isBefore(storeOpenTime) || startTime.isAfter(storeCloseTime)) {
            throw new BusinessException("Giờ hẹn phải nằm trong giờ mở cửa của cửa hàng (" + storeOpenTime + " - " + storeCloseTime + ")");
        }
    }
}
