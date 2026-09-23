package com.core.beautyshop.modules.spa.application.service.impl;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.core.beautyshop.modules.identity.api.IdentityFacade;
import com.core.beautyshop.modules.order.api.OrderFacade;
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
    private final OrderFacade orderFacade;

    @Override
    @Transactional
    public AppointmentResponse bookAppointment(BookAppointmentRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException("Cần chọn ít nhất một dịch vụ để đặt lịch");
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();
        UserSummaryDto user = identityFacade.getUserSummaryById(currentUserId);

        validateDateTimeNotPast(request.getAppointmentDate(), request.getStartTime());
        checkStoreSchedule(request.getStartTime(), null);

        Appointment appointment = Appointment.builder()
                .userId(currentUserId)
                .appointmentDate(request.getAppointmentDate())
                .startTime(request.getStartTime())
                .status(AppointmentStatus.PENDING)
                .notes(request.getNotes())
                .build();

        List<AppointmentItem> items = new ArrayList<>();
        LocalTime currentStartTime = request.getStartTime();

        request.getItems().stream().map(com.core.beautyshop.modules.spa.application.dto.request.AppointmentItemRequest::getStaffId)
                .filter(java.util.Objects::nonNull).distinct().sorted()
                .forEach(id -> staffRepository.findByIdWithLock(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Staff not found")));
        request.getItems().stream().map(com.core.beautyshop.modules.spa.application.dto.request.AppointmentItemRequest::getTicketId)
                .filter(java.util.Objects::nonNull).distinct().sorted()
                .forEach(id -> ticketRepository.findByIdForUpdate(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Ticket not found")));

        for (var itemReq : request.getItems()) {
            BeautyService service = beautyServiceRepository.findById(itemReq.getServiceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dịch vụ"));
            
            Staff staff = null;
            if (itemReq.getStaffId() != null) {
                staff = staffRepository.findByIdWithLock(itemReq.getStaffId())
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên"));
            }
            
            validateService(service);
            if (staff != null) validateStaff(staff, service);
            LocalTime itemEndTime = serviceEnd(currentStartTime, service);

            if (staff != null) {
                boolean isOverlapping = !appointmentRepository.findOverlappingAppointmentsForStaffWithLock(
                        staff.getId(),
                        request.getAppointmentDate(),
                        currentStartTime,
                        itemEndTime
                ).isEmpty();
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
                ticket = ticketRepository.findByIdForUpdate(itemReq.getTicketId())
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin vé liệu trình với ID: " + itemReq.getTicketId()));

                if (!ticket.getUserId().equals(currentUserId)) {
                    throw new BusinessException("Vé liệu trình này không thuộc sở hữu của bạn!");
                }

                if (ticket.getOrderId() == null
                        || !orderFacade.isPaidOrderForUser(ticket.getOrderId(), currentUserId)) {
                    throw new BusinessException("Vé liệu trình không gắn với đơn hàng đã thanh toán hợp lệ");
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

                var entitlement = ticket.getEntitlements().get(service.getId());
                if (entitlement == null || entitlement.getUsed() >= entitlement.getTotal()) {
                    throw new BusinessException("No remaining ticket sessions for this service");
                }
                entitlement.setUsed(entitlement.getUsed() + 1);

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

        checkStoreSchedule(request.getStartTime(), currentStartTime);

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
        Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch hẹn"));
                
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!appointment.getUserId().equals(currentUserId) && !SecurityUtils.isAdmin()) {
            throw new AccessDeniedException("Bạn không có quyền hủy lịch hẹn này!");
        }
        
        if (appointment.getStatus() != AppointmentStatus.PENDING && appointment.getStatus() != AppointmentStatus.CONFIRMED
                && appointment.getStatus() != AppointmentStatus.CANCELLED) {
            throw new BusinessException("Lịch hẹn đã hoàn tất, không thể hủy!");
        }
        
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            return;
        }

        java.time.LocalDateTime appointmentStartTime = appointment.getAppointmentDate().atTime(appointment.getStartTime());
        if (appointmentStartTime.isBefore(java.time.LocalDateTime.now()) && !SecurityUtils.isAdmin()) {
            throw new BusinessException("Không thể hủy lịch hẹn đã qua thời gian bắt đầu!");
        }

        if (appointment.getItems() != null) {
            var lockedTickets = lockTicketsInOrder(appointment.getItems());
            for (AppointmentItem item : appointment.getItems()) {
                if (item.getTicket() != null) {
                    UserServiceTicket ticket = lockedTickets.get(item.getTicket().getId());
                    restoreEntitlement(ticket, item);
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

        var users = identityFacade.findUserSummaries(page.getContent().stream().map(Appointment::getUserId).distinct().toList());
        return page.map(apt -> {
            String customerName = java.util.Optional.ofNullable(users.get(apt.getUserId()))
                    .map(UserSummaryDto::getFullName).orElse(null);
            return AppointmentResponse.of(apt, customerName);
        });
    }

    @Override
    @Transactional
    public AppointmentResponse updateAppointmentStatus(Long id, com.core.beautyshop.modules.spa.application.dto.request.UpdateAppointmentStatusRequest request) {
        Appointment appointment = appointmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch hẹn với ID: " + id));

        AppointmentStatus previousStatus = appointment.getStatus();
        AppointmentStatus newStatus = request.getStatus();

        if (previousStatus == newStatus) {
            return AppointmentResponse.of(appointment, identityFacade.findUserSummaryById(appointment.getUserId())
                    .map(UserSummaryDto::getFullName).orElse(null));
        }
        boolean valid = switch (previousStatus) {
            case PENDING -> newStatus == AppointmentStatus.CONFIRMED || newStatus == AppointmentStatus.CANCELLED;
            case CONFIRMED -> newStatus == AppointmentStatus.IN_PROGRESS || newStatus == AppointmentStatus.CANCELLED
                    || newStatus == AppointmentStatus.NO_SHOW;
            case IN_PROGRESS -> newStatus == AppointmentStatus.COMPLETED;
            default -> false;
        };
        if (!valid) {
            throw new BusinessException("Lịch hẹn đã hoàn tất, không thể thay đổi trạng thái!");
        }

        if (newStatus == AppointmentStatus.CANCELLED && previousStatus != AppointmentStatus.CANCELLED) {
            if (appointment.getItems() != null) {
                var lockedTickets = lockTicketsInOrder(appointment.getItems());
                for (AppointmentItem item : appointment.getItems()) {
                    if (item.getTicket() != null) {
                        UserServiceTicket ticket = lockedTickets.get(item.getTicket().getId());
                        restoreEntitlement(ticket, item);
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

        if (newStatus == AppointmentStatus.CONFIRMED || newStatus == AppointmentStatus.IN_PROGRESS) {
            var assignments = request.getStaffAssignments() == null ? java.util.Map.<Long, Long>of() : request.getStaffAssignments();
            for (Long itemId : assignments.keySet()) {
                if (appointment.getItems().stream().noneMatch(item -> itemId.equals(item.getId()))) {
                    throw new BusinessException("Staff assignment contains an unknown appointment item");
                }
            }
            var staffIds = appointment.getItems().stream().map(item -> assignments.getOrDefault(item.getId(),
                    item.getStaff() == null ? null : item.getStaff().getId())).toList();
            if (staffIds.stream().anyMatch(java.util.Objects::isNull)) {
                throw new BusinessException("Assign qualified staff before confirming an appointment");
            }
            var lockedStaff = new java.util.HashMap<Long, Staff>();
            staffIds.stream().distinct().sorted().forEach(staffId -> lockedStaff.put(staffId,
                    staffRepository.findByIdWithLock(staffId).orElseThrow(() -> new BusinessException("Staff not found"))));
            for (int i = 0; i < appointment.getItems().size(); i++) {
                var item = appointment.getItems().get(i);
                Staff staff = lockedStaff.get(staffIds.get(i));
                validateService(item.getService()); validateStaff(staff, item.getService());
                if (!appointmentRepository.findOverlappingAppointmentsForStaffExcludingAppointmentWithLock(
                        staff.getId(), appointment.getAppointmentDate(), item.getStartTime(), item.getEndTime(), appointment.getId()).isEmpty()) {
                    throw new BusinessException("Staff already has an appointment in this time slot");
                }
                item.setStaff(staff);
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
        Appointment appointment = appointmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch hẹn với ID: " + id));

        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!appointment.getUserId().equals(currentUserId) && !SecurityUtils.isAdmin()) {
            throw new AccessDeniedException("Bạn không có quyền thay đổi lịch hẹn này!");
        }

        if (appointment.getStatus() != AppointmentStatus.PENDING
                && appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new BusinessException("Chỉ có thể đổi lịch hẹn đang chờ xác nhận hoặc đã xác nhận!");
        }

        java.time.LocalDateTime originalStart = appointment.getAppointmentDate().atTime(appointment.getStartTime());
        if (originalStart.isBefore(java.time.LocalDateTime.now()) && !SecurityUtils.isAdmin()) {
            throw new BusinessException("Không thể đổi lịch hẹn đã qua thời gian bắt đầu!");
        }

        validateDateTimeNotPast(request.getAppointmentDate(), request.getStartTime());
        checkStoreSchedule(request.getStartTime(), null);

        appointment.getItems().stream()
                .map(AppointmentItem::getStaff)
                .filter(java.util.Objects::nonNull)
                .map(Staff::getId)
                .distinct()
                .sorted()
                .forEach(staffId -> staffRepository.findByIdWithLock(staffId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Không tìm thấy nhân viên với ID: " + staffId)));

        appointment.setAppointmentDate(request.getAppointmentDate());
        
        LocalTime currentStartTime = request.getStartTime();
        for (AppointmentItem item : appointment.getItems()) {
            item.setStartTime(currentStartTime);
            
            validateService(item.getService());
            if (item.getStaff() != null) validateStaff(item.getStaff(), item.getService());
            LocalTime itemEndTime = serviceEnd(currentStartTime, item.getService());
            item.setEndTime(itemEndTime);
            
            if (item.getStaff() != null) {
                boolean isOverlapping = !appointmentRepository.findOverlappingAppointmentsForStaffExcludingAppointmentWithLock(
                        item.getStaff().getId(),
                        request.getAppointmentDate(),
                        currentStartTime,
                        itemEndTime,
                        appointment.getId()
                ).isEmpty();
                if (isOverlapping) {
                    String staffName = identityFacade.findUserSummaryById(item.getStaff().getUserId())
                            .map(UserSummaryDto::getFullName)
                            .orElse("Nhân viên");
                    throw new BusinessException(
                            staffName + " đã có lịch hẹn khác trong khung giờ "
                                    + currentStartTime + " - " + itemEndTime
                                    + " ngày " + request.getAppointmentDate()
                                    + ". Vui lòng chọn khung giờ khác!");
                }
            }
            currentStartTime = itemEndTime;
        }

        checkStoreSchedule(request.getStartTime(), currentStartTime);

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

    private void restoreEntitlement(UserServiceTicket ticket, AppointmentItem item) {
        if (item.getService() == null) throw new BusinessException("Appointment service is missing");
        var entitlement = ticket.getEntitlements().get(item.getService().getId());
        if (entitlement == null || entitlement.getUsed() <= 0 || ticket.getUsedSessions() <= 0) {
            throw new BusinessException("Ticket entitlement is inconsistent; reconciliation required");
        }
        entitlement.setUsed(entitlement.getUsed() - 1);
    }

    private java.util.Map<Long, UserServiceTicket> lockTicketsInOrder(List<AppointmentItem> items) {
        java.util.Map<Long, UserServiceTicket> locked = new java.util.HashMap<>();
        items.stream()
                .map(AppointmentItem::getTicket)
                .filter(java.util.Objects::nonNull)
                .map(UserServiceTicket::getId)
                .distinct()
                .sorted()
                .forEach(ticketId -> locked.put(ticketId,
                        ticketRepository.findByIdForUpdate(ticketId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                        "Không tìm thấy thông tin vé liệu trình với ID: " + ticketId))));
        return locked;
    }

    private void validateService(BeautyService service) {
        if (!Boolean.TRUE.equals(service.getIsActive()) || Boolean.TRUE.equals(service.getIsDeleted())) {
            throw new BusinessException("Service is not available");
        }
    }

    private void validateStaff(Staff staff, BeautyService service) {
        if (!Boolean.TRUE.equals(staff.getIsActive()) || Boolean.TRUE.equals(staff.getIsDeleted())
                || staff.getSkills() == null || staff.getSkills().stream().noneMatch(skill ->
                    !Boolean.TRUE.equals(skill.getIsDeleted()) && skill.getService().getId().equals(service.getId()))) {
            throw new BusinessException("Staff is not qualified or available for this service");
        }
    }

    private LocalTime serviceEnd(LocalTime start, BeautyService service) {
        long duration = (long) service.getDurationMinutes()
                + (service.getPreparationTimeMinutes() == null ? 0 : service.getPreparationTimeMinutes());
        long available = java.time.Duration.between(start, LocalTime.of(20, 0)).toMinutes();
        if (service.getDurationMinutes() <= 0 || duration <= 0 || duration > available) {
            throw new BusinessException("Service must finish within opening hours");
        }
        return start.plusMinutes(duration);
    }

    private void validateDateTimeNotPast(LocalDate appointmentDate, LocalTime startTime) {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        if (appointmentDate.isBefore(today) || (appointmentDate.isEqual(today) && startTime.isBefore(now))) {
            throw new BusinessException("Không thể đặt hoặc điều chỉnh lịch hẹn vào thời điểm trong quá khứ!");
        }
    }

    private void checkStoreSchedule(LocalTime startTime, LocalTime endTime) {
        LocalTime storeOpenTime = LocalTime.of(8, 0);
        LocalTime storeCloseTime = LocalTime.of(20, 0);
        
        if (startTime.isBefore(storeOpenTime) || startTime.isAfter(storeCloseTime)) {
            throw new BusinessException("Giờ bắt đầu phải nằm trong giờ mở cửa của cửa hàng (" + storeOpenTime + " - " + storeCloseTime + ")");
        }
        if (endTime != null && endTime.isAfter(storeCloseTime)) {
            throw new BusinessException("Dịch vụ dự kiến kết thúc lúc " + endTime + ", vượt quá giờ đóng cửa của cửa hàng (" + storeCloseTime + ")");
        }
    }
}
