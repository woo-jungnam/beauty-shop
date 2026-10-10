package com.core.beautyshop.modules.spa.application.service.impl;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import com.core.beautyshop.modules.identity.api.IdentityFacade;
import com.core.beautyshop.modules.order.api.OrderFacade;
import com.core.beautyshop.modules.identity.api.dto.UserSummaryDto;
import com.core.beautyshop.modules.spa.application.dto.request.BookAppointmentRequest;
import com.core.beautyshop.modules.spa.application.dto.response.AppointmentResponse;
import com.core.beautyshop.modules.spa.application.service.AppointmentService;
import com.core.beautyshop.modules.spa.application.service.SpaTimeRules;
import com.core.beautyshop.modules.spa.domain.StaffScheduleRepository;
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
    private final StaffScheduleRepository schedules;
    private final com.core.beautyshop.modules.spa.application.service.SpaAccessService access;
    private final com.core.beautyshop.modules.spa.application.service.SpaPreparationService preparation;
    private final com.core.beautyshop.modules.spa.application.service.FacilitySchedulingService facilities;
    private final com.core.beautyshop.modules.spa.application.service.SpaBookingPolicyService policies;
    private final com.core.beautyshop.modules.spa.application.service.TicketUsageService usage;
    private final com.core.beautyshop.modules.spa.domain.AppointmentActionHistoryRepository history;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponse bookAppointment(BookAppointmentRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException("Cần chọn ít nhất một dịch vụ để đặt lịch");
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long bookingUserId = (request.getTargetUserId() != null && canManageAppointments())
                ? request.getTargetUserId()
                : currentUserId;
        UserSummaryDto user = identityFacade.getUserSummaryById(bookingUserId);

        if (!canManageAppointments()) {
            validateDateTimeNotPast(request.getAppointmentDate(), request.getStartTime());
        } else {
            if (request.getAppointmentDate().isBefore(LocalDate.now(SpaTimeRules.ZONE))) {
                throw new BusinessException("Không thể đặt lịch trong quá khứ");
            }
        }
        checkStoreSchedule(request.getStartTime(), null);

        Appointment appointment = Appointment.builder()
                .userId(bookingUserId)
                .appointmentDate(request.getAppointmentDate())
                .startTime(request.getStartTime())
                .status(AppointmentStatus.PENDING)
                .notes(request.getNotes())
                .build();

        policies.snapshot(appointment);
        List<AppointmentItem> items = new ArrayList<>();
        LocalTime currentStartTime = request.getStartTime();

        request.getItems().stream().map(com.core.beautyshop.modules.spa.application.dto.request.AppointmentItemRequest::getStaffId)
                .filter(java.util.Objects::nonNull).distinct().sorted()
                .forEach(id -> staffRepository.findByIdWithLock(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Staff not found")));
        request.getItems().stream().map(com.core.beautyshop.modules.spa.application.dto.request.AppointmentItemRequest::getTicketId)
                .filter(java.util.Objects::nonNull).distinct().sorted()
                .forEach(id -> ticketRepository.findByIdForUpdate(id)
                        .filter(t -> !Boolean.TRUE.equals(t.getIsDeleted()))
                        .orElseThrow(() -> new ResourceNotFoundException("Ticket not found")));
        request.getItems().stream().map(com.core.beautyshop.modules.spa.application.dto.request.AppointmentItemRequest::getServiceId)
                .filter(java.util.Objects::nonNull).distinct().sorted()
                .forEach(id -> beautyServiceRepository.findAvailableByIdForUpdate(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Service not found")));

        for (var itemReq : request.getItems()) {
            BeautyService service = beautyServiceRepository.findAvailableByIdForUpdate(itemReq.getServiceId())
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
                requireWorkingShift(staff, request.getAppointmentDate(), currentStartTime, itemEndTime);
                // The staff row is the scheduling mutex; a locking JOIN could deadlock with rescheduling.
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
                ticket = ticketRepository.findByIdForUpdate(itemReq.getTicketId())
                        .filter(t -> !Boolean.TRUE.equals(t.getIsDeleted()))
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin vé liệu trình với ID: " + itemReq.getTicketId()));

                if (!ticket.getUserId().equals(bookingUserId)) {
                    throw new BusinessException("Vé liệu trình này không thuộc sở hữu của khách hàng!");
                }

                if (ticket.getOrderId() == null
                        || !orderFacade.isPaidOrderForUser(ticket.getOrderId(), bookingUserId)) {
                    throw new BusinessException("Vé liệu trình không gắn với đơn hàng đã thanh toán hợp lệ");
                }

                if (ticket.getStatus() != TicketStatus.ACTIVE) {
                    throw new BusinessException("Vé liệu trình không ở trạng thái hoạt động (trạng thái hiện tại: " + ticket.getStatus() + ")");
                }

                if (ticket.getExpiryDate() != null && ticket.getExpiryDate().isBefore(Instant.now())) {
                    throw new BusinessException("Vé liệu trình đã hết hạn sử dụng vào ngày " + ticket.getExpiryDate());
                }

                requireTicketExpiry(ticket, request.getAppointmentDate(), currentStartTime);
                itemPrice = BigDecimal.ZERO;
            }

            AppointmentItem item = AppointmentItem.builder()
                    .appointment(appointment)
                    .service(service)
                    .serviceNameSnapshot(service.getName())
                    .staff(staff)
                    .ticket(ticket)
                    .price(itemPrice)
                    .startTime(currentStartTime)
                    .endTime(itemEndTime)
                    .build();
            
            if (itemReq.getFacilityId() != null) item.setFacility(facilities.findFacility(itemReq.getFacilityId()));
            usage.requireCanReserve(item);
            items.add(item);
            currentStartTime = itemEndTime;
        }

        checkStoreSchedule(request.getStartTime(), currentStartTime);

        if (request.getTargetUserId() != null || !canManageAppointments()) {
            if (appointmentRepository.existsOverlappingAppointmentForUser(
                    bookingUserId,
                    request.getAppointmentDate(),
                    request.getStartTime(),
                    currentStartTime)) {
                throw new BusinessException("Khách hàng đã có một lịch hẹn khác trong khung giờ "
                        + request.getStartTime() + " - " + currentStartTime
                        + " ngày " + request.getAppointmentDate()
                        + ". Vui lòng chọn khung giờ khác!");
            }
        }

        appointment.setEndTime(currentStartTime);
        appointment.setItems(items);

        facilities.snapshotRequirements(items);
        facilities.assign(items, appointment.getAppointmentDate(), null);
        Appointment saved = appointmentRepository.saveAndFlush(appointment);
        preparation.snapshotRequirements(saved);
        for (AppointmentItem item : saved.getItems()) { usage.reserve(item); usage.recordReservation(item); }
        appendHistory(saved, null, "BOOK", null, saved.getStatus().name(), "Policy: " + policies.forAppointment(saved).version());
        var staffNames = resolveStaffNames(List.of(saved));
        return AppointmentResponse.of(saved, user.getFullName(), user.getPhone(), staffNames);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentResponse> getMyAppointments() {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        UserSummaryDto user = identityFacade.findUserSummaryById(currentUserId).orElse(null);
        String customerName = user != null ? user.getFullName() : null;
        String customerPhone = user != null ? user.getPhone() : null;

        List<Appointment> list = appointmentRepository.findByUserIdOrderByAppointmentDateDescStartTimeDesc(currentUserId).stream()
                .filter(a -> !Boolean.TRUE.equals(a.getIsDeleted()))
                .toList();
        var staffNames = resolveStaffNames(list);
        return list.stream()
                .map(apt -> AppointmentResponse.of(apt, customerName, customerPhone, staffNames))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentResponse getAppointmentById(Long id) {
        Appointment appointment = appointmentRepository.findByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch hẹn với ID: " + id));

        access.requireCanViewAppointment(appointment);

        UserSummaryDto user = identityFacade.findUserSummaryById(appointment.getUserId()).orElse(null);
        String customerName = user != null ? user.getFullName() : null;
        String customerPhone = user != null ? user.getPhone() : null;
        var staffNames = resolveStaffNames(List.of(appointment));

        return AppointmentResponse.of(appointment, customerName, customerPhone, staffNames);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cancelAppointment(Long appointmentId) {
        cancelAppointment(appointmentId, null);
    }

    @Override @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cancelAppointment(Long appointmentId, String reason) {
        Appointment appointment = locked(appointmentId);
        if (!appointment.getUserId().equals(SecurityUtils.getCurrentUserId()) && !canManageAppointments())
            throw new AccessDeniedException("You cannot cancel this appointment");
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) return;
        if (appointment.getStatus() != AppointmentStatus.PENDING && appointment.getStatus() != AppointmentStatus.CONFIRMED)
            throw new BusinessException("Only pending or confirmed appointments can be cancelled");
        if (!canManageAppointments()) requireCutoff(appointment, policies.forAppointment(appointment).cancelCutoffMinutes());
        else if (!appointment.getUserId().equals(SecurityUtils.getCurrentUserId()) || !Instant.now().isBefore(scheduledStart(appointment)
                .minusSeconds(policies.forAppointment(appointment).cancelCutoffMinutes() * 60L)))
            requireActionReason(reason);
        cancelLocked(appointment, reason == null || reason.isBlank() ? "Customer cancellation requested" : reason, "CANCEL");
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void expirePending(Long id) {
        Appointment appointment = locked(id);
        if (appointment.getStatus() == AppointmentStatus.PENDING && appointment.getPendingExpiresAt() != null
                && !appointment.getPendingExpiresAt().isAfter(Instant.now()))
            cancelLocked(appointment, "Pending confirmation expired", "EXPIRE_PENDING");
    }

    private void cancelLocked(Appointment appointment, String reason, String action) {
        AppointmentStatus old = appointment.getStatus();
        lockItemTickets(appointment.getItems());
        for (AppointmentItem item : appointment.getItems()) {
            usage.release(item, reason);
            item.setExecutionStatus(com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.SKIPPED);
            item.setExecutionNotes(reason);
        }
        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointmentRepository.save(appointment);
        appendHistory(appointment, null, action, old.name(), "CANCELLED", reason);
    }

    @Override
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<AppointmentResponse> getAllAppointments(
            java.time.LocalDate date, AppointmentStatus status, org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Page<Appointment> page;
        if (!canManageAppointments()) {
            if (!access.hasRole("SPA_THERAPIST")) throw new AccessDeniedException("Appointment management access required");
            page = appointmentRepository.findAssignedAppointments(SecurityUtils.getCurrentUserId(), date, status, pageable);
        } else {
            page = appointmentRepository.findManagedAppointments(date, status, pageable);
        }

        var users = identityFacade.findUserSummaries(page.getContent().stream().map(Appointment::getUserId).distinct().toList());
        var staffNames = resolveStaffNames(page.getContent());
        return page.map(apt -> {
            UserSummaryDto u = users.get(apt.getUserId());
            String customerName = u != null ? u.getFullName() : null;
            String customerPhone = u != null ? u.getPhone() : null;
            return AppointmentResponse.of(apt, customerName, customerPhone, staffNames);
        });
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponse updateAppointmentStatus(Long id, com.core.beautyshop.modules.spa.application.dto.request.UpdateAppointmentStatusRequest request) {
        Appointment appointment = locked(id);
        AppointmentStatus previous = appointment.getStatus(), next = request.getStatus();
        boolean assignments = request.getStaffAssignments() != null && !request.getStaffAssignments().isEmpty();
        if (!canManageAppointments() && !(access.hasRole("SPA_THERAPIST") && access.isAssignedTechnician(appointment)
                && !assignments && request.getNotes() == null && (next == AppointmentStatus.IN_PROGRESS || next == AppointmentStatus.COMPLETED)))
            throw new AccessDeniedException("Appointment action is outside your role");
        if (next == null) throw new BusinessException("Appointment status is required");
        if (previous == next && !assignments && (request.getNotes() == null || request.getNotes().isBlank())) return response(appointment);
        boolean valid = previous == next && (previous == AppointmentStatus.PENDING || previous == AppointmentStatus.CONFIRMED || previous == AppointmentStatus.IN_PROGRESS);
        valid |= switch (previous) {
            case PENDING -> next == AppointmentStatus.CONFIRMED || next == AppointmentStatus.CANCELLED || next == AppointmentStatus.COMPLETED;
            case CONFIRMED -> next == AppointmentStatus.IN_PROGRESS || next == AppointmentStatus.CANCELLED || next == AppointmentStatus.NO_SHOW || next == AppointmentStatus.COMPLETED;
            case IN_PROGRESS -> next == AppointmentStatus.COMPLETED;
            default -> false;
        };
        if (!valid) throw new BusinessException("Invalid appointment transition");
        if (assignments && next != AppointmentStatus.CONFIRMED) throw new BusinessException("Assign staff before starting services");
        if (next == AppointmentStatus.CANCELLED) {
            requireActionReason(request.getNotes());
            cancelLocked(appointment, request.getNotes(), "CANCEL");
            return response(appointment);
        }
        if (next == AppointmentStatus.CONFIRMED || next == AppointmentStatus.IN_PROGRESS) {
            if (previous != AppointmentStatus.IN_PROGRESS) validateAndAssignStaff(appointment, request.getStaffAssignments());
            facilities.assign(appointment.getItems(), appointment.getAppointmentDate(), appointment.getId());
        }
        if (next == AppointmentStatus.IN_PROGRESS && previous != next) {
            if (appointment.getCheckedInAt() == null) throw new BusinessException("Record customer check-in before starting the visit");
            if (scheduledStart(appointment).minusSeconds(3600).isAfter(Instant.now())) {
                throw new BusinessException("Chưa đến giờ hẹn (chỉ có thể bắt đầu trước tối đa 60 phút)");
            }
            appointment.setActualStartedAt(now());
        }
        if (next == AppointmentStatus.COMPLETED && previous != next) {
            if (appointment.getCheckedInAt() == null) {
                appointment.setCheckedInAt(now());
                appointment.setCheckedInByUserId(SecurityUtils.getCurrentUserId());
            }
            if (appointment.getActualStartedAt() == null) {
                appointment.setActualStartedAt(now());
            }
            lockItemTickets(appointment.getItems());
            for (AppointmentItem item : appointment.getItems()) {
                if (item.getExecutionStatus() != com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.PERFORMED
                        && item.getExecutionStatus() != com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.SKIPPED) {
                    if (item.getTicket() != null && item.getTicketUsageState() == com.core.beautyshop.modules.spa.domain.enums.TicketUsageState.RESERVED) {
                        usage.consume(item, false, request.getNotes() == null ? "Completed appointment" : request.getNotes());
                    }
                    item.setExecutionStatus(com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.PERFORMED);
                    if (item.getActualStartedAt() == null) item.setActualStartedAt(now());
                    if (item.getActualCompletedAt() == null) item.setActualCompletedAt(now());
                    if (item.getPerformedByUserId() == null) item.setPerformedByUserId(SecurityUtils.getCurrentUserId());
                    if (item.getExecutionNotes() == null) item.setExecutionNotes("Completed by reception");
                }
            }
            appointment.setActualCompletedAt(now());
        }
        if (next == AppointmentStatus.NO_SHOW) {
            if (appointment.getCheckedInAt() != null) throw new BusinessException("A checked-in customer cannot be marked no-show");
            var policy = policies.forAppointment(appointment);
            if (scheduledStart(appointment).plusSeconds(policy.noShowGraceMinutes() * 60L).isAfter(Instant.now()))
                throw new BusinessException("No-show grace period has not ended");
            if (request.getNotes() == null || request.getNotes().isBlank()) throw new BusinessException("No-show reason is required");
            lockItemTickets(appointment.getItems());
            for (AppointmentItem item : appointment.getItems()) {
                if (policy.noShowQuotaAction().equals("RELEASE")) usage.release(item, request.getNotes());
                else usage.consume(item, true, request.getNotes());
                item.setExecutionStatus(com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.NO_SHOW);
                item.setExecutionNotes(request.getNotes());
            }
        }
        appointment.setStatus(next);
        if (next != AppointmentStatus.PENDING) appointment.setPendingExpiresAt(null);
        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            String notes = appointment.getNotes() == null ? request.getNotes() : appointment.getNotes() + "; " + request.getNotes();
            if (notes.length() > 500) throw new BusinessException("Appointment notes must be at most 500 characters");
            appointment.setNotes(notes);
        }
        appointmentRepository.save(appointment);
        appendHistory(appointment, null, assignments ? "ASSIGN_STAFF" : "CHANGE_STATUS", previous.name(), next.name(), request.getNotes());
        return response(appointment);
    }

    private void validateAndAssignStaff(Appointment appointment, java.util.Map<Long, Long> requested) {
        var assignments = requested == null ? java.util.Map.<Long, Long>of() : requested;
        for (Long itemId : assignments.keySet()) if (appointment.getItems().stream().noneMatch(item -> itemId.equals(item.getId())))
            throw new BusinessException("Staff assignment contains an unknown appointment item");
        var ids = appointment.getItems().stream().map(item -> assignments.getOrDefault(item.getId(), item.getStaff() == null ? null : item.getStaff().getId())).toList();
        if (ids.stream().anyMatch(java.util.Objects::isNull)) throw new BusinessException("Assign qualified staff before confirmation");
        var locked = new java.util.HashMap<Long, Staff>();
        ids.stream().distinct().sorted().forEach(staffId -> locked.put(staffId, staffRepository.findByIdWithLock(staffId)
                .orElseThrow(() -> new BusinessException("Staff not found"))));
        for (int i = 0; i < appointment.getItems().size(); i++) {
            AppointmentItem item = appointment.getItems().get(i); Staff staff = locked.get(ids.get(i));
            validateService(item.getService()); validateStaff(staff, item.getService());
            requireWorkingShift(staff, appointment.getAppointmentDate(), item.getStartTime(), item.getEndTime());
            if (appointmentRepository.existsOverlappingAppointmentForStaffExcludingAppointment(staff.getId(), appointment.getAppointmentDate(), item.getStartTime(), item.getEndTime(), appointment.getId()))
                throw new BusinessException("Staff already has an appointment in this time slot");
            item.setStaff(staff);
        }
    }

    @Override @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponse checkIn(Long id) {
        if (!canManageAppointments()) throw new AccessDeniedException("Reception access required");
        Appointment a = locked(id);
        if (a.getStatus() != AppointmentStatus.CONFIRMED) throw new BusinessException("Check-in requires a confirmed appointment");
        if (!a.getAppointmentDate().equals(LocalDate.now(SpaTimeRules.ZONE))) throw new BusinessException("Check-in must be recorded on the appointment date");
        if (a.getCheckedInAt() == null) {
            a.setCheckedInAt(now()); a.setCheckedInByUserId(SecurityUtils.getCurrentUserId());
            appointmentRepository.save(a); appendHistory(a, null, "CHECK_IN", null, "CHECKED_IN", null);
        }
        return response(a);
    }

    @Override @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponse executeItem(Long id, Long itemId, com.core.beautyshop.modules.spa.application.dto.request.ExecuteAppointmentItemRequest request) {
        Appointment a = locked(id);
        AppointmentItem item = a.getItems().stream().filter(i -> itemId.equals(i.getId())).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Appointment item not found"));
        access.requireCanPerformItem(item);
        if (a.getStatus() != AppointmentStatus.IN_PROGRESS) throw new BusinessException("Start the checked-in visit before recording services");
        var old = item.getExecutionStatus(); var target = request.getStatus();
        if (old == target) return response(a);
        boolean start = old == com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.PLANNED && target == com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.IN_PROGRESS;
        boolean perform = old == com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.IN_PROGRESS && target == com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.PERFORMED;
        boolean skip = old == com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.PLANNED && target == com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.SKIPPED;
        if (!start && !perform && !skip) throw new BusinessException("Invalid service execution transition");
        if (skip && (request.getNotes() == null || request.getNotes().isBlank())) throw new BusinessException("Skipping a service requires a reason");
        if (start) {
            if (item.getStaff() == null) throw new BusinessException("Assign a therapist before starting services");
            Staff performer = staffRepository.findByIdWithLock(item.getStaff().getId()).orElseThrow(() -> new BusinessException("Staff not found"));
            validateStaff(performer, item.getService());
            if (appointmentRepository.hasActiveExecutionForStaff(performer.getId(), item.getId()))
                throw new BusinessException("Therapist is still performing another service");
            if (a.getItems().stream().anyMatch(i -> i.getStartTime().isBefore(item.getStartTime()) && i.getExecutionStatus() == com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.PLANNED))
                throw new BusinessException("Record the outcome of earlier services before starting the next one");
            if (a.getItems().stream().anyMatch(i -> i != item && i.getExecutionStatus() == com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.IN_PROGRESS))
                throw new BusinessException("Finish the current service before starting the next one");
            preparation.requireReadyForStart(item);
            if (item.getTicket() != null) {
                lockItemTickets(a.getItems());
                if (item.getTicket().getStatus() == TicketStatus.REVOKED || !orderFacade.isPaidOrderForUser(item.getTicket().getOrderId(), a.getUserId()))
                    throw new BusinessException("Ticket is revoked or unpaid");
                if ("SERVICE_START".equals(item.getTicket().getExpiryCheckMode()) && item.getTicket().getExpiryDate() != null && !item.getTicket().getExpiryDate().isAfter(Instant.now()))
                    throw new BusinessException("Ticket expired before service execution");
            }
            facilities.requireExecutionCapacity(item);
            item.setActualStartedAt(now()); item.setPerformedByUserId(SecurityUtils.getCurrentUserId());
        }
        if (perform || skip) {
            lockItemTickets(a.getItems());
            if (perform) { usage.consume(item, false, request.getNotes()); item.setActualCompletedAt(now()); }
            else usage.release(item, request.getNotes());
        }
        item.setExecutionStatus(target); item.setExecutionNotes(request.getNotes());
        appointmentRepository.save(a); appendHistory(a, item, "SERVICE_" + target.name(), old.name(), target.name(), request.getNotes());
        return response(a);
    }

    @Override @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<com.core.beautyshop.modules.spa.domain.AppointmentActionHistory> actionHistory(Long id, org.springframework.data.domain.Pageable page) {
        access.requireCanViewAppointment(lockedForRead(id));
        return history.findByAppointmentIdOrderByIdDesc(id, page);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponse rescheduleAppointment(Long id, com.core.beautyshop.modules.spa.application.dto.request.RescheduleAppointmentRequest request) {
        Appointment appointment = locked(id);

        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!appointment.getUserId().equals(currentUserId) && !canManageAppointments()) {
            throw new AccessDeniedException("Bạn không có quyền thay đổi lịch hẹn này!");
        }

        if (appointment.getStatus() != AppointmentStatus.PENDING
                && appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new BusinessException("Chỉ có thể đổi lịch hẹn đang chờ xác nhận hoặc đã xác nhận!");
        }

        if (appointment.getCheckedInAt() != null) throw new BusinessException("Cannot reschedule a checked-in visit");
        if (!canManageAppointments()) requireCutoff(appointment, policies.forAppointment(appointment).rescheduleCutoffMinutes());
        else if (!appointment.getUserId().equals(currentUserId) || !Instant.now().isBefore(scheduledStart(appointment)
                .minusSeconds(policies.forAppointment(appointment).rescheduleCutoffMinutes() * 60L)))
            requireActionReason(request.getNotes());
        String originalInterval = appointment.getAppointmentDate() + " " + appointment.getStartTime() + "-" + appointment.getEndTime();

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

        lockItemTickets(appointment.getItems());

        appointment.setAppointmentDate(request.getAppointmentDate());
        
        LocalTime currentStartTime = request.getStartTime();
        for (AppointmentItem item : appointment.getItems()) {
            if (item.getStartTime() == null || item.getEndTime() == null) {
                throw new BusinessException("Original appointment interval is missing; reconciliation required");
            }
            long reservedMinutes = java.time.Duration.between(item.getStartTime(), item.getEndTime()).toMinutes();
            if (reservedMinutes <= 0 || reservedMinutes > java.time.Duration.between(currentStartTime, SpaTimeRules.CLOSE).toMinutes()) {
                throw new BusinessException("Rescheduled service must fit opening hours");
            }
            validateService(item.getService());
            if (item.getStaff() != null) validateStaff(item.getStaff(), item.getService());
            LocalTime itemEndTime = currentStartTime.plusMinutes(reservedMinutes);
            if (item.getTicket() != null) requireTicketExpiry(item.getTicket(), request.getAppointmentDate(), currentStartTime);
            item.setStartTime(currentStartTime);
            item.setEndTime(itemEndTime);
            
            if (item.getStaff() != null) {
                requireWorkingShift(item.getStaff(), request.getAppointmentDate(), currentStartTime, itemEndTime);
                boolean isOverlapping = appointmentRepository.existsOverlappingAppointmentForStaffExcludingAppointment(
                        item.getStaff().getId(),
                        request.getAppointmentDate(),
                        currentStartTime,
                        itemEndTime,
                        appointment.getId()
                );
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

        if (appointmentRepository.existsOverlappingAppointmentForUserExcluding(
                appointment.getUserId(),
                appointment.getId(),
                request.getAppointmentDate(),
                request.getStartTime(),
                currentStartTime)) {
            throw new BusinessException("Khách hàng đã có một lịch hẹn khác trong khung giờ "
                    + request.getStartTime() + " - " + currentStartTime
                    + " ngày " + request.getAppointmentDate()
                    + ". Vui lòng chọn khung giờ khác!");
        }

        appointment.setStartTime(request.getStartTime());
        appointment.setEndTime(currentStartTime);

        if (request.getNotes() != null && !request.getNotes().isEmpty()) {
            appointment.setNotes(request.getNotes());
        }

        facilities.validateOrReassignForReschedule(appointment.getItems(), appointment.getAppointmentDate(), appointment.getId());
        Appointment saved = appointmentRepository.save(appointment);
        appendHistory(saved, null, "RESCHEDULE", null, null, originalInterval + " -> " + request.getAppointmentDate() + " " + request.getStartTime());
        return response(saved);
    }

    private java.util.Map<Long, String> resolveStaffNames(List<Appointment> appointmentList) {
        if (appointmentList == null || appointmentList.isEmpty()) return java.util.Map.of();
        List<Staff> staffList = appointmentList.stream()
                .filter(a -> a.getItems() != null)
                .flatMap(a -> a.getItems().stream())
                .map(AppointmentItem::getStaff)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (staffList.isEmpty()) return java.util.Map.of();
        List<Long> userIds = staffList.stream()
                .map(Staff::getUserId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        var userSummaries = identityFacade.findUserSummaries(userIds);
        java.util.Map<Long, String> map = new java.util.HashMap<>();
        for (Staff s : staffList) {
            if (s.getUserId() != null && userSummaries.containsKey(s.getUserId())) {
                String name = userSummaries.get(s.getUserId()).getFullName();
                if (name != null && !name.isBlank()) {
                    map.put(s.getId(), name);
                }
            }
        }
        return map;
    }

    private Appointment locked(Long id) { return appointmentRepository.findByIdForUpdate(id).filter(a -> !Boolean.TRUE.equals(a.getIsDeleted()))
            .orElseThrow(() -> new ResourceNotFoundException("Appointment not found")); }
    private Appointment lockedForRead(Long id) { return appointmentRepository.findByIdWithItems(id).filter(a -> !Boolean.TRUE.equals(a.getIsDeleted()))
            .orElseThrow(() -> new ResourceNotFoundException("Appointment not found")); }
    private AppointmentResponse response(Appointment a) {
        UserSummaryDto user = identityFacade.findUserSummaryById(a.getUserId()).orElse(null);
        String customerName = user != null ? user.getFullName() : null;
        String customerPhone = user != null ? user.getPhone() : null;
        var staffNames = resolveStaffNames(List.of(a));
        return AppointmentResponse.of(a, customerName, customerPhone, staffNames);
    }
    private Instant now() { return Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS); }
    private Instant scheduledStart(Appointment a) { return a.getAppointmentDate().atTime(a.getStartTime()).atZone(SpaTimeRules.ZONE).toInstant(); }
    private void requireCutoff(Appointment a, int minutes) {
        if (!Instant.now().isBefore(scheduledStart(a).minusSeconds(minutes * 60L))) throw new BusinessException("The change/cancellation cutoff has passed");
    }
    private void requireActionReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500) throw new BusinessException("Staff adjustment requires a reason of at most 500 characters");
    }
    private void requireTicketExpiry(UserServiceTicket ticket, LocalDate date, LocalTime start) {
        Instant expiry = ticket.getExpiryDate();
        if (expiry == null) return;
        Instant target = "SERVICE_START".equals(ticket.getExpiryCheckMode()) ? date.atTime(start).atZone(SpaTimeRules.ZONE).toInstant() : Instant.now();
        if (!target.isBefore(expiry)) throw new BusinessException("Ticket expiry does not cover this booking");
    }
    private void lockItemTickets(List<AppointmentItem> items) {
        var locked = lockTicketsInOrder(items);
        items.stream().filter(i -> i.getTicket() != null).forEach(i -> i.setTicket(locked.get(i.getTicket().getId())));
    }
    private void appendHistory(Appointment a, AppointmentItem item, String action, String from, String to, String details) {
        var row = new com.core.beautyshop.modules.spa.domain.AppointmentActionHistory();
        row.setAppointmentId(a.getId()); row.setAppointmentItemId(item == null ? null : item.getId());
        row.setActorUserId(SecurityUtils.getCurrentUserIdOptional().orElse(null)); row.setAction(action);
        row.setFromState(from != null && from.length() <= 40 ? from : null); row.setToState(to);
        row.setDetails(details); history.save(row);
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
                                .filter(t -> !Boolean.TRUE.equals(t.getIsDeleted()))
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
        return SpaTimeRules.end(start, service);
    }

    private void validateDateTimeNotPast(LocalDate appointmentDate, LocalTime startTime) {
        SpaTimeRules.requireFuture(appointmentDate, startTime);
    }

    private void checkStoreSchedule(LocalTime startTime, LocalTime endTime) {
        LocalTime storeOpenTime = SpaTimeRules.OPEN;
        LocalTime storeCloseTime = SpaTimeRules.CLOSE;
        
        if (startTime.isBefore(storeOpenTime) || startTime.isAfter(storeCloseTime)) {
            throw new BusinessException("Giờ bắt đầu phải nằm trong giờ mở cửa của cửa hàng (" + storeOpenTime + " - " + storeCloseTime + ")");
        }
        if (endTime != null && endTime.isAfter(storeCloseTime)) {
            throw new BusinessException("Dịch vụ dự kiến kết thúc lúc " + endTime + ", vượt quá giờ đóng cửa của cửa hàng (" + storeCloseTime + ")");
        }
    }

    private void requireWorkingShift(Staff staff, LocalDate date, LocalTime start, LocalTime end) {
        if (schedules.hasScheduleOnDate(staff.getId(), date)) {
            if (!schedules.coversWorkingInterval(staff.getId(), date, start, end)) {
                throw new BusinessException("Staff has no working shift covering the complete service interval");
            }
        } else {
            if (start.isBefore(SpaTimeRules.OPEN) || end.isAfter(SpaTimeRules.CLOSE)) {
                throw new BusinessException("Service interval is outside store opening hours");
            }
        }
    }

    private boolean canManageAppointments() { return access.canManageReception(); }
}
