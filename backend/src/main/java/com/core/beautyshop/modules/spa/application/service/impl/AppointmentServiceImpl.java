package com.core.beautyshop.modules.spa.application.service.impl;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.core.beautyshop.modules.notification.api.CustomerNotificationFacade;
import com.core.beautyshop.modules.spa.application.dto.request.AppointmentItemRequest;
import com.core.beautyshop.modules.spa.application.dto.request.BookAppointmentRequest;
import com.core.beautyshop.modules.spa.application.dto.request.ExecuteAppointmentItemRequest;
import com.core.beautyshop.modules.spa.application.dto.request.RescheduleAppointmentRequest;
import com.core.beautyshop.modules.spa.application.dto.request.UpdateAppointmentStatusRequest;
import com.core.beautyshop.modules.spa.application.dto.response.AppointmentResponse;
import com.core.beautyshop.modules.spa.application.service.AppointmentService;
import com.core.beautyshop.modules.spa.application.service.FacilitySchedulingService;
import com.core.beautyshop.modules.spa.application.service.SpaAccessService;
import com.core.beautyshop.modules.spa.application.service.SpaBookingPolicyService;
import com.core.beautyshop.modules.spa.application.service.SpaBusinessCalendarService;
import com.core.beautyshop.modules.spa.application.service.SpaConsumableService;
import com.core.beautyshop.modules.spa.application.service.SpaFinancialService;
import com.core.beautyshop.modules.spa.application.service.SpaLoyaltyService;
import com.core.beautyshop.modules.spa.application.service.SpaPreparationService;
import com.core.beautyshop.modules.spa.application.service.SpaTimeRules;
import com.core.beautyshop.modules.spa.application.service.TicketUsageService;
import com.core.beautyshop.modules.spa.application.service.TreatmentAppointmentBridge;
import com.core.beautyshop.modules.spa.domain.Appointment;
import com.core.beautyshop.modules.spa.domain.AppointmentActionHistory;
import com.core.beautyshop.modules.spa.domain.AppointmentActionHistoryRepository;
import com.core.beautyshop.modules.spa.domain.AppointmentItem;
import com.core.beautyshop.modules.spa.domain.AppointmentRepository;
import com.core.beautyshop.modules.spa.domain.BeautyService;
import com.core.beautyshop.modules.spa.domain.BeautyServiceRepository;
import com.core.beautyshop.modules.spa.domain.Staff;
import com.core.beautyshop.modules.spa.domain.StaffRepository;
import com.core.beautyshop.modules.spa.domain.StaffScheduleRepository;
import com.core.beautyshop.modules.spa.domain.UserServiceTicket;
import com.core.beautyshop.modules.spa.domain.UserServiceTicketRepository;
import com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus;
import com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus;
import com.core.beautyshop.modules.spa.domain.enums.TicketStatus;
import com.core.beautyshop.modules.identity.api.IdentityFacade;
import com.core.beautyshop.modules.identity.api.dto.UserSummaryDto;
import com.core.beautyshop.modules.order.api.OrderFacade;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppointmentServiceImpl implements AppointmentService {
    @Autowired
    private SpaLoyaltyService loyalty;
    @Autowired
    private CustomerNotificationFacade customerInbox;

    private final AppointmentRepository appointmentRepository;
    private final BeautyServiceRepository beautyServiceRepository;
    private final StaffRepository staffRepository;
    private final UserServiceTicketRepository ticketRepository;
    private final IdentityFacade identityFacade;
    private final OrderFacade orderFacade;
    private final StaffScheduleRepository schedules;
    private final SpaAccessService access;
    private final SpaPreparationService preparation;
    private final FacilitySchedulingService facilities;
    private final SpaBookingPolicyService policies;
    private final TicketUsageService usage;
    private final AppointmentActionHistoryRepository history;
    private final TreatmentAppointmentBridge treatments;
    private final SpaBusinessCalendarService calendar;
    private final SpaConsumableService materials;
    private final SpaFinancialService finance;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponse bookAppointment(BookAppointmentRequest request) {
        calendar.lock();
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException("Cần chọn ít nhất một dịch vụ để đặt lịch");
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();
        boolean allowTargetUser = canManageAppointments()
                || (request.getTargetUserId() != null && treatments.canStaffBookForCustomer(currentUserId, request.getTargetUserId(), request.getItems()));
        Long bookingUserId = (request.getTargetUserId() != null && allowTargetUser)
                ? request.getTargetUserId()
                : currentUserId;
        UserSummaryDto user = identityFacade.getUserSummaryById(bookingUserId);

        if (!allowTargetUser) {
            validateDateTimeNotPast(request.getAppointmentDate(), request.getStartTime());
            requireBookingHorizon(request.getAppointmentDate(), policies.current());
            long pendingCount = appointmentRepository.countByUserIdAndStatusAndIsDeletedFalse(bookingUserId, AppointmentStatus.PENDING);
            if (pendingCount >= 2) {
                throw new BusinessException("Bạn đang có " + pendingCount + " lịch hẹn đang chờ xác nhận. Vui lòng hoàn tất hoặc hủy lịch cũ trước khi đặt thêm lịch mới.");
            }
        } else {
            LocalDate today = LocalDate.now(SpaTimeRules.ZONE);
            if (request.getAppointmentDate().isBefore(today)) {
                throw new BusinessException("Không thể đặt lịch trong quá khứ");
            }
            if (request.getAppointmentDate().equals(today)
                    && request.getStartTime().isBefore(LocalTime.now(SpaTimeRules.ZONE))) {
                throw new BusinessException("Giờ bắt đầu không thể ở trong quá khứ");
            }
        }
        checkStoreSchedule(request.getAppointmentDate(), request.getStartTime(), null);

        String effectiveNotes = request.getNotes();
        if ((request.getCustomerName() != null && !request.getCustomerName().isBlank())
                || (request.getCustomerPhone() != null && !request.getCustomerPhone().isBlank())) {
            String namePart = request.getCustomerName() != null ? request.getCustomerName().trim() : "";
            String phonePart = request.getCustomerPhone() != null ? request.getCustomerPhone().trim() : "";
            String contactTag = "[Khách: " + namePart + (namePart.isEmpty() || phonePart.isEmpty() ? "" : " · ") + phonePart + "]";
            effectiveNotes = (effectiveNotes != null && !effectiveNotes.isBlank())
                    ? contactTag + " " + effectiveNotes
                    : contactTag;
            if (effectiveNotes.length() > 500) {
                effectiveNotes = effectiveNotes.substring(0, 500);
            }
        }

        Appointment appointment = Appointment.builder()
                .userId(bookingUserId)
                .appointmentDate(request.getAppointmentDate())
                .startTime(request.getStartTime())
                .status(AppointmentStatus.PENDING)
                .notes(effectiveNotes)
                .build();

        policies.snapshot(appointment);
        List<AppointmentItem> items = new ArrayList<>();
        LocalTime currentStartTime = request.getStartTime();

        var sessionIds = request.getItems().stream()
                .map(AppointmentItemRequest::getTreatmentSessionItemId)
                .filter(Objects::nonNull).toList();
        if (sessionIds.stream().distinct().count() != sessionIds.size())
            throw new BusinessException("Một mục điều trị không thể xuất hiện hai lần trong cùng lịch hẹn");
        request.getItems().stream().sorted(Comparator.comparing(
                AppointmentItemRequest::getTreatmentSessionItemId,
                Comparator.nullsFirst(Comparator.naturalOrder())))
                .forEach(item -> treatments.validateAndLockBooking(item.getTreatmentSessionItemId(), bookingUserId,
                        item.getServiceId(), item.getTicketId(), request.getAppointmentDate()
                                .atTime(request.getStartTime()).atZone(SpaTimeRules.ZONE).toInstant()));

        request.getItems().stream().map(AppointmentItemRequest::getStaffId)
                .filter(Objects::nonNull).distinct().sorted()
                .forEach(id -> staffRepository.findByIdWithLock(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Staff not found")));
        request.getItems().stream().map(AppointmentItemRequest::getTicketId)
                .filter(Objects::nonNull).distinct().sorted()
                .forEach(id -> ticketRepository.findByIdForUpdate(id)
                        .filter(t -> !Boolean.TRUE.equals(t.getIsDeleted()))
                        .orElseThrow(() -> new ResourceNotFoundException("Ticket not found")));
        request.getItems().stream().map(AppointmentItemRequest::getServiceId)
                .filter(Objects::nonNull).distinct().sorted()
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
            if (staff != null) validateStaff(staff, service, request.getAppointmentDate());
            LocalTime itemEndTime = serviceEnd(currentStartTime, service);
            treatments.validateAndLockBooking(itemReq.getTreatmentSessionItemId(), bookingUserId,
                    itemReq.getServiceId(), itemReq.getTicketId(), request.getAppointmentDate()
                            .atTime(currentStartTime).atZone(SpaTimeRules.ZONE).toInstant());

            if (staff != null) {
                requireWorkingShift(staff, request.getAppointmentDate(), currentStartTime, itemEndTime);
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
                    .treatmentSessionItemId(itemReq.getTreatmentSessionItemId())
                    .price(itemPrice)
                    .startTime(currentStartTime)
                    .endTime(itemEndTime)
                    .build();
            
            if (itemReq.getFacilityId() != null) item.setFacility(facilities.findFacility(itemReq.getFacilityId()));
            usage.requireCanReserve(item);
            items.add(item);
            currentStartTime = itemEndTime;
        }

        checkStoreSchedule(request.getAppointmentDate(), request.getStartTime(), currentStartTime);

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
        for (AppointmentItem item : saved.getItems())
            if (item.getTreatmentSessionItemId() != null)
                treatments.linkBooking(item.getTreatmentSessionItemId(), item.getId());
        preparation.snapshotRequirements(saved);
        materials.snapshot(saved);
        BigDecimal depositRequired = finance.snapshot(saved, request.getAcceptedFinancialPolicy(), request.getFinancialPolicyVersion(), request.getPaymentOption(), canManageAppointments());
        if (depositRequired != null && depositRequired.compareTo(BigDecimal.ZERO) > 0) {
            saved.setPendingExpiresAt(Instant.now().plus(15, java.time.temporal.ChronoUnit.MINUTES));
            appointmentRepository.save(saved);
        }
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
            throw new AccessDeniedException("Bạn không có quyền hủy lịch hẹn này");
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) return;
        if (appointment.getStatus() != AppointmentStatus.PENDING && appointment.getStatus() != AppointmentStatus.CONFIRMED)
            throw new BusinessException("Chỉ có thể hủy lịch hẹn đang chờ xác nhận hoặc đã xác nhận");
        if (appointment.getCheckedInAt() != null) {
            if (!canManageAppointments()) {
                throw new BusinessException("Khách hàng đã check-in tại cơ sở, không thể tự hủy lịch. Vui lòng liên hệ quầy tiếp đón!");
            }
            if (reason == null || reason.trim().length() < 10) {
                throw new BusinessException("Hủy lịch hẹn đã check-in bắt buộc phải nhập lý do cụ thể (tối thiểu 10 ký tự) để phục vụ đối soát.");
            }
        }
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
        boolean charge = !"EXPIRE_PENDING".equals(action) && !Instant.now().isBefore(scheduledStart(appointment)
                .minusSeconds(policies.forAppointment(appointment).cancelCutoffMinutes()*60L));
        finance.close(appointment,false,charge);
        treatments.lockAppointmentCourse(appointment);
        AppointmentStatus old = appointment.getStatus();
        lockItemTickets(appointment.getItems());
        for (AppointmentItem item : appointment.getItems()) {
            usage.release(item, reason);
            item.setExecutionStatus(com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.SKIPPED);
            item.setExecutionNotes(reason);
            treatments.releaseBooking(item);
        }
        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointmentRepository.save(appointment);
        appendHistory(appointment, null, action, old.name(), "CANCELLED", reason);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AppointmentResponse> getAllAppointments(
            LocalDate date, AppointmentStatus status, Pageable pageable) {
        Page<Appointment> page;
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
    public AppointmentResponse updateAppointmentStatus(Long id, UpdateAppointmentStatusRequest request) {
        Appointment appointment = locked(id);
        treatments.lockAppointmentCourse(appointment);
        AppointmentStatus previous = appointment.getStatus(), next = request.getStatus();
        boolean assignments = request.getStaffAssignments() != null && !request.getStaffAssignments().isEmpty();
        if (!canManageAppointments() && !(access.hasRole("SPA_THERAPIST") && access.isAssignedTechnician(appointment)
                && !assignments && request.getNotes() == null && (next == AppointmentStatus.IN_PROGRESS || next == AppointmentStatus.COMPLETED)))
            throw new AccessDeniedException("Appointment action is outside your role");
        if (next == null) throw new BusinessException("Trạng thái lịch hẹn là bắt buộc");
        if (previous == next && !assignments && (request.getNotes() == null || request.getNotes().isBlank())) return response(appointment);
        boolean valid = previous == next && (previous == AppointmentStatus.PENDING || previous == AppointmentStatus.CONFIRMED || previous == AppointmentStatus.IN_PROGRESS);
        valid |= switch (previous) {
            case PENDING -> next == AppointmentStatus.CONFIRMED || next == AppointmentStatus.CANCELLED;
            case CONFIRMED -> next == AppointmentStatus.IN_PROGRESS || next == AppointmentStatus.CANCELLED || next == AppointmentStatus.NO_SHOW;
            case IN_PROGRESS -> next == AppointmentStatus.COMPLETED;
            default -> false;
        };
        if (!valid) throw new BusinessException("Chuyển trạng thái lịch hẹn không hợp lệ");
        boolean isReassignDuringInProgress = previous == AppointmentStatus.IN_PROGRESS && next == AppointmentStatus.IN_PROGRESS && assignments;
        if (assignments && next != AppointmentStatus.CONFIRMED && !isReassignDuringInProgress) 
            throw new BusinessException("Vui lòng phân công chuyên viên trước khi bắt đầu dịch vụ");
        if (next == AppointmentStatus.CANCELLED) {
            if (appointment.getCheckedInAt() != null && (request.getNotes() == null || request.getNotes().trim().length() < 10)) {
                throw new BusinessException("Hủy lịch hẹn đã check-in bắt buộc phải nhập lý do cụ thể (tối thiểu 10 ký tự) để đối soát.");
            }
            requireActionReason(request.getNotes());
            cancelLocked(appointment, request.getNotes(), "CANCEL");
            return response(appointment);
        }
        if (next == AppointmentStatus.CONFIRMED || next == AppointmentStatus.IN_PROGRESS) {
            finance.requireDeposit(appointment);
            if (isReassignDuringInProgress) {
                reassignStaffForInProgressAppointment(appointment, request.getStaffAssignments(), request.getNotes());
            } else if (previous != AppointmentStatus.IN_PROGRESS) {
                validateAndAssignStaff(appointment, request.getStaffAssignments());
            }
            facilities.assign(appointment.getItems(), appointment.getAppointmentDate(), appointment.getId());
            if (isAppointmentPast(appointment) && next == AppointmentStatus.IN_PROGRESS) {
                if (appointment.getCheckedInAt() == null) {
                    throw new BusinessException("Lịch hẹn đã quá giờ kết thúc và khách chưa check-in. Vui lòng chuyển sang Vắng mặt hoặc Hủy.");
                }
                requireActionReason(request.getNotes());
            }
            if (isAppointmentPast(appointment) && next == AppointmentStatus.CONFIRMED) {
                requireActionReason(request.getNotes());
            }
        }
        if (next == AppointmentStatus.IN_PROGRESS && previous != next) {
            treatments.validateAppointmentStart(appointment);
            if (appointment.getCheckedInAt() == null) throw new BusinessException("Vui lòng ghi nhận check-in của khách trước khi bắt đầu buổi hẹn");
            if (scheduledStart(appointment).minusSeconds(3600).isAfter(Instant.now())) {
                throw new BusinessException("Chưa đến giờ hẹn (chỉ có thể bắt đầu trước tối đa 60 phút)");
            }
            appointment.setActualStartedAt(now());
        }
        if (next == AppointmentStatus.COMPLETED && previous != next) {
            if (previous != AppointmentStatus.IN_PROGRESS || appointment.getItems().stream().anyMatch(item ->
                    item.getExecutionStatus() != com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.PERFORMED
                    && item.getExecutionStatus() != com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.SKIPPED))
                throw new BusinessException("Buổi hẹn phải được bắt đầu và ghi kết quả từng dịch vụ trước khi hoàn tất");
            if (appointment.getCheckedInAt()==null || appointment.getActualStartedAt()==null)
                throw new BusinessException("Cần check-in và ghi nhận bắt đầu buổi hẹn trước khi hoàn tất");
            appointment.setActualCompletedAt(now());
        }
        if (next == AppointmentStatus.NO_SHOW) {
            finance.close(appointment,true,false);
            if (appointment.getCheckedInAt() != null) throw new BusinessException("Khách đã check-in không thể đánh dấu là vắng mặt");
            var policy = policies.forAppointment(appointment);
            if (scheduledStart(appointment).plusSeconds(policy.noShowGraceMinutes() * 60L).isAfter(Instant.now()))
                throw new BusinessException("Chưa hết thời gian chờ vắng mặt của lịch hẹn");
            if (request.getNotes() == null || request.getNotes().isBlank()) throw new BusinessException("Lý do vắng mặt là bắt buộc");
            lockItemTickets(appointment.getItems());
            for (AppointmentItem item : appointment.getItems()) {
                if (policy.noShowQuotaAction().equals("RELEASE")) usage.release(item, request.getNotes());
                else usage.consume(item, true, request.getNotes());
                item.setExecutionStatus(com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.NO_SHOW);
                item.setExecutionNotes(request.getNotes());
                treatments.releaseBooking(item);
            }
        }
        appointment.setStatus(next);
        if (next != AppointmentStatus.PENDING) appointment.setPendingExpiresAt(null);
        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            String incoming = request.getNotes().trim();
            if (incoming.length() > 500) throw new BusinessException("Ghi chú lịch hẹn không được quá 500 ký tự");
            String existing = appointment.getNotes();
            if (existing == null || existing.isBlank()) {
                appointment.setNotes(incoming);
            } else {
                String combined = existing + "; " + incoming;
                if (combined.length() > 500) {
                    appointment.setNotes(incoming);
                } else {
                    appointment.setNotes(combined);
                }
            }
        }
        appointmentRepository.save(appointment);
        appendHistory(appointment, null, isReassignDuringInProgress ? "REASSIGN_STAFF" : (assignments ? "ASSIGN_STAFF" : "CHANGE_STATUS"), previous.name(), next.name(), request.getNotes());
        return response(appointment);
    }

    private void reassignStaffForInProgressAppointment(Appointment appointment, Map<Long, Long> requested, String notes) {
        if (!canManageAppointments()) throw new AccessDeniedException("Cần quyền tiếp đón/quản trị Spa để điều chuyển chuyên viên");
        requireActionReason(notes);
        if (requested == null || requested.isEmpty()) return;
        for (Map.Entry<Long, Long> entry : requested.entrySet()) {
            Long itemId = entry.getKey();
            Long newStaffId = entry.getValue();
            AppointmentItem item = appointment.getItems().stream()
                    .filter(i -> itemId.equals(i.getId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("Dịch vụ không tồn tại trong lịch hẹn"));
            if (item.getExecutionStatus() != AppointmentItemExecutionStatus.PLANNED) {
                throw new BusinessException("Chỉ có thể điều chuyển chuyên viên cho dịch vụ chưa bắt đầu (PLANNED)");
            }
            Staff newStaff = staffRepository.findByIdWithLock(newStaffId)
                    .orElseThrow(() -> new BusinessException("Không tìm thấy chuyên viên"));
            validateService(item.getService());
            validateStaff(newStaff, item.getService(), appointment.getAppointmentDate());
            requireWorkingShift(newStaff, appointment.getAppointmentDate(), item.getStartTime(), item.getEndTime());
            if (appointmentRepository.existsOverlappingAppointmentForStaffExcludingAppointment(
                    newStaff.getId(), appointment.getAppointmentDate(), item.getStartTime(), item.getEndTime(), appointment.getId())) {
                throw new BusinessException("Chuyên viên đã có lịch phục vụ khác trong khung giờ này");
            }
            item.setStaff(newStaff);
        }
    }

    private void validateAndAssignStaff(Appointment appointment, Map<Long, Long> requested) {
        var assignments = requested == null ? Map.<Long, Long>of() : requested;
        for (Long itemId : assignments.keySet()) if (appointment.getItems().stream().noneMatch(item -> itemId.equals(item.getId())))
            throw new BusinessException("Phân công chuyên viên chứa dịch vụ không tồn tại trong lịch hẹn");
        var ids = appointment.getItems().stream().map(item -> assignments.getOrDefault(item.getId(), item.getStaff() == null ? null : item.getStaff().getId())).toList();
        if (ids.stream().anyMatch(Objects::isNull)) throw new BusinessException("Vui lòng chọn chuyên viên đủ chuyên môn trước khi xác nhận");
        var locked = new HashMap<Long, Staff>();
        ids.stream().distinct().sorted().forEach(staffId -> locked.put(staffId, staffRepository.findByIdWithLock(staffId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy chuyên viên"))));
        for (int i = 0; i < appointment.getItems().size(); i++) {
            AppointmentItem item = appointment.getItems().get(i); Staff staff = locked.get(ids.get(i));
            validateService(item.getService()); validateStaff(staff, item.getService(), appointment.getAppointmentDate());
            requireWorkingShift(staff, appointment.getAppointmentDate(), item.getStartTime(), item.getEndTime());
            if (appointmentRepository.existsOverlappingAppointmentForStaffExcludingAppointment(staff.getId(), appointment.getAppointmentDate(), item.getStartTime(), item.getEndTime(), appointment.getId()))
                throw new BusinessException("Chuyên viên đã có lịch phục vụ khác trong khung giờ này");
            item.setStaff(staff);
        }
    }

    @Override @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponse checkIn(Long id) {
        if (!canManageAppointments()) throw new AccessDeniedException("Reception access required");
        Appointment a = locked(id);
        if (a.getStatus() != AppointmentStatus.CONFIRMED) throw new BusinessException("Chỉ có thể check-in cho lịch hẹn đã được xác nhận");
        if (!a.getAppointmentDate().equals(LocalDate.now(SpaTimeRules.ZONE))) throw new BusinessException("Chỉ có thể ghi nhận check-in vào đúng ngày hẹn");
        if (a.getCheckedInAt() == null) {
            a.setCheckedInAt(now()); a.setCheckedInByUserId(SecurityUtils.getCurrentUserId());
            appointmentRepository.save(a); appendHistory(a, null, "CHECK_IN", null, "CHECKED_IN", null);
        }
        return response(a);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponse addInSessionItem(Long appointmentId, AppointmentItemRequest request) {
        calendar.lock();
        if (!canManageAppointments()) throw new AccessDeniedException("Cần quyền tiếp đón/quản trị Spa để thêm dịch vụ phát sinh");
        if (request == null || request.getServiceId() == null) throw new BusinessException("Thông tin dịch vụ phát sinh là bắt buộc");
        Appointment appointment = locked(appointmentId);
        treatments.lockAppointmentCourse(appointment);

        if (appointment.getStatus() != AppointmentStatus.CONFIRMED && appointment.getStatus() != AppointmentStatus.IN_PROGRESS) {
            throw new BusinessException("Chỉ có thể thêm dịch vụ phát sinh khi buổi hẹn đang mở (CONFIRMED hoặc IN_PROGRESS)");
        }

        BeautyService service = beautyServiceRepository.findAvailableByIdForUpdate(request.getServiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dịch vụ"));
        validateService(service);

        LocalTime currentStartTime = appointment.getItems().stream()
                .map(AppointmentItem::getEndTime)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(appointment.getStartTime());

        LocalTime itemEndTime = serviceEnd(currentStartTime, service);
        if (itemEndTime.isAfter(calendar.forDate(appointment.getAppointmentDate()).closesAt())) {
            throw new BusinessException("Dịch vụ phát sinh vượt quá khung giờ mở cửa của cơ sở");
        }

        Staff staff = null;
        if (request.getStaffId() != null) {
            staff = staffRepository.findByIdWithLock(request.getStaffId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyên viên"));
            validateStaff(staff, service, appointment.getAppointmentDate());
            requireWorkingShift(staff, appointment.getAppointmentDate(), currentStartTime, itemEndTime);
            if (appointmentRepository.existsOverlappingAppointmentForStaffExcludingAppointment(
                    staff.getId(), appointment.getAppointmentDate(), currentStartTime, itemEndTime, appointment.getId())) {
                throw new BusinessException("Chuyên viên đã có lịch phục vụ khác trong khung giờ " + currentStartTime + " - " + itemEndTime);
            }
        }

        UserServiceTicket ticket = null;
        BigDecimal itemPrice = service.getBasePrice();
        if (request.getTicketId() != null) {
            ticket = ticketRepository.findByIdForUpdate(request.getTicketId())
                    .filter(t -> !Boolean.TRUE.equals(t.getIsDeleted()))
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin vé liệu trình với ID: " + request.getTicketId()));
            if (!ticket.getUserId().equals(appointment.getUserId())) {
                throw new BusinessException("Vé liệu trình này không thuộc sở hữu của khách hàng!");
            }
            if (ticket.getStatus() != TicketStatus.ACTIVE) {
                throw new BusinessException("Vé liệu trình không ở trạng thái hoạt động");
            }
            if (ticket.getExpiryDate() != null && ticket.getExpiryDate().isBefore(Instant.now())) {
                throw new BusinessException("Vé liệu trình đã hết hạn sử dụng");
            }
            requireTicketExpiry(ticket, appointment.getAppointmentDate(), currentStartTime);
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
                .executionStatus(AppointmentItemExecutionStatus.PLANNED)
                .build();

        if (request.getFacilityId() != null) {
            item.setFacility(facilities.findFacility(request.getFacilityId()));
        }
        usage.requireCanReserve(item);

        appointment.getItems().add(item);
        if (appointment.getEndTime() == null || itemEndTime.isAfter(appointment.getEndTime())) {
            appointment.setEndTime(itemEndTime);
        }

        facilities.assign(appointment.getItems(), appointment.getAppointmentDate(), appointment.getId());
        Appointment saved = appointmentRepository.saveAndFlush(appointment);
        preparation.snapshotRequirements(saved);
        materials.snapshotItem(item);

        if (ticket != null) {
            usage.reserve(item);
            usage.recordReservation(item);
        }

        appendHistory(saved, item, "ADD_IN_SESSION_ITEM", saved.getStatus().name(), saved.getStatus().name(), "Thêm dịch vụ phát sinh: " + service.getName());
        var staffNames = resolveStaffNames(List.of(saved));
        UserSummaryDto user = identityFacade.getUserSummaryById(saved.getUserId());
        return AppointmentResponse.of(saved, user != null ? user.getFullName() : null, user != null ? user.getPhone() : null, staffNames);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void rateAppointment(Long appointmentId, int stars, String comment) {
        if (stars < 1 || stars > 5) throw new BusinessException("Điểm đánh giá phải từ 1 đến 5 sao");
        Appointment appointment = locked(appointmentId);
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!appointment.getUserId().equals(currentUserId)) {
            throw new AccessDeniedException("Chỉ khách hàng của lịch hẹn mới có thể gửi đánh giá");
        }
        if (appointment.getStatus() != AppointmentStatus.COMPLETED) {
            throw new BusinessException("Chỉ có thể đánh giá cho buổi hẹn đã hoàn thành (COMPLETED)");
        }
        if (history.existsByAppointmentIdAndAction(appointmentId, "RATE_EXPERIENCE")) {
            throw new BusinessException("Buổi hẹn này đã được đánh giá");
        }
        Set<Staff> ratedStaff = appointment.getItems().stream()
                .map(AppointmentItem::getStaff)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (Staff staff : ratedStaff) {
            Staff lockedStaff = staffRepository.findByIdWithLock(staff.getId()).orElse(null);
            if (lockedStaff != null) {
                int count = lockedStaff.getTotalReviews() == null ? 0 : lockedStaff.getTotalReviews();
                double currentRating = lockedStaff.getRating() == null ? 0.0 : lockedStaff.getRating();
                double newRating = ((currentRating * count) + stars) / (count + 1.0);
                lockedStaff.setTotalReviews(count + 1);
                lockedStaff.setRating(Math.round(newRating * 10.0) / 10.0);
                staffRepository.save(lockedStaff);
            }
        }
        appendHistory(appointment, null, "RATE_EXPERIENCE", "COMPLETED", "COMPLETED", "Đánh giá " + stars + " sao" + (comment != null && !comment.isBlank() ? ": " + comment.trim() : ""));
    }

    @Override @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponse executeItem(Long id, Long itemId, ExecuteAppointmentItemRequest request) {
        Appointment a = locked(id);
        treatments.lockAppointmentCourse(a);
        AppointmentItem item = a.getItems().stream().filter(i -> itemId.equals(i.getId())).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dịch vụ trong lịch hẹn"));
        access.requireCanPerformItem(item);
        if (item.getTreatmentSessionItemId() != null && !access.hasRole("ADMIN")
                && (item.getStaff() == null || !Objects.equals(item.getStaff().getUserId(), SecurityUtils.getCurrentUserId())))
            throw new AccessDeniedException("Chỉ chuyên viên được phân công được ghi kết quả mục điều trị này");
        if (a.getStatus() != AppointmentStatus.IN_PROGRESS) throw new BusinessException("Vui lòng bắt đầu buổi hẹn trước khi ghi nhận trạng thái thực hiện dịch vụ");
        var old = item.getExecutionStatus(); var target = request.getStatus();
        if (old == target) return response(a);
        boolean start = old == AppointmentItemExecutionStatus.PLANNED && target == AppointmentItemExecutionStatus.IN_PROGRESS;
        boolean perform = old == AppointmentItemExecutionStatus.IN_PROGRESS && target == AppointmentItemExecutionStatus.PERFORMED;
        boolean skip = old == AppointmentItemExecutionStatus.PLANNED && target == AppointmentItemExecutionStatus.SKIPPED;
        boolean interrupt = old == AppointmentItemExecutionStatus.IN_PROGRESS
                && target == AppointmentItemExecutionStatus.SKIPPED;
        skip |= interrupt;
        if (!start && !perform && !skip) throw new BusinessException("Chuyển trạng thái thực hiện dịch vụ không hợp lệ");
        if (skip && (request.getNotes() == null || request.getNotes().isBlank())) throw new BusinessException("Bỏ qua dịch vụ cần phải có lý do cụ thể");
        if (start) {
            if (item.getTreatmentSessionItemId() != null) treatments.validateExecution(item);
            if (item.getStaff() == null) throw new BusinessException("Vui lòng phân công chuyên viên trước khi bắt đầu dịch vụ");
            Staff performer = staffRepository.findByIdWithLock(item.getStaff().getId()).orElseThrow(() -> new BusinessException("Không tìm thấy chuyên viên"));
            validateStaff(performer, item.getService(), a.getAppointmentDate());
            if (appointmentRepository.hasActiveExecutionForStaff(performer.getId(), item.getId()))
                throw new BusinessException("Chuyên viên đang thực hiện một dịch vụ khác chưa kết thúc");
            if (a.getItems().stream().anyMatch(i -> i.getStartTime().isBefore(item.getStartTime()) && i.getExecutionStatus() == AppointmentItemExecutionStatus.PLANNED))
                throw new BusinessException("Cần hoàn tất hoặc ghi nhận dịch vụ trước đó trước khi bắt đầu dịch vụ tiếp theo");
            if (a.getItems().stream().anyMatch(i -> i != item && i.getExecutionStatus() == com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.IN_PROGRESS))
                throw new BusinessException("Vui lòng hoàn thành dịch vụ hiện tại trước khi bắt đầu dịch vụ tiếp theo");
            preparation.requireReadyForStart(item);
            if (item.getTicket() != null) {
                lockItemTickets(a.getItems());
                if (item.getTicket().getStatus() == TicketStatus.REVOKED || !orderFacade.isPaidOrderForUser(item.getTicket().getOrderId(), a.getUserId()))
                    throw new BusinessException("Vé liệu trình đã bị thu hồi hoặc chưa thanh toán");
                if ("SERVICE_START".equals(item.getTicket().getExpiryCheckMode()) && item.getTicket().getExpiryDate() != null && !item.getTicket().getExpiryDate().isAfter(Instant.now()))
                    throw new BusinessException("Vé liệu trình đã hết hạn trước khi thực hiện dịch vụ");
            }
            facilities.requireExecutionCapacity(item);
            item.setActualStartedAt(now()); item.setPerformedByUserId(SecurityUtils.getCurrentUserId());
        }
        if (perform || skip) {
            lockItemTickets(a.getItems());
            if (perform) { materials.requireRecorded(item); usage.consume(item, false, request.getNotes()); item.setActualCompletedAt(now()); }
            else {
                usage.release(item, request.getNotes());
                if (interrupt) item.setActualCompletedAt(now());
            }
        }
        item.setExecutionStatus(target); item.setExecutionNotes(request.getNotes());
        if (perform && item.getTicket()!=null && item.getTreatmentSessionItemId()==null && loyalty!=null) loyalty.performedTicket(item.getTicket());
        if (item.getTreatmentSessionItemId() != null) treatments.synchronizeExecution(item);
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
        calendar.lock();
        Appointment appointment = locked(id);
        treatments.lockAppointmentCourse(appointment);

        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!appointment.getUserId().equals(currentUserId) && !canManageAppointments()) {
            throw new AccessDeniedException("Bạn không có quyền thay đổi lịch hẹn này!");
        }

        if (appointment.getStatus() != AppointmentStatus.PENDING
                && appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new BusinessException("Chỉ có thể đổi lịch hẹn đang chờ xác nhận hoặc đã xác nhận!");
        }

        if (appointment.getCheckedInAt() != null) throw new BusinessException("Không thể dời lịch hẹn khi khách đã check-in");
        if (!canManageAppointments()) requireCutoff(appointment, policies.forAppointment(appointment).rescheduleCutoffMinutes());
        else if (!appointment.getUserId().equals(currentUserId) || !Instant.now().isBefore(scheduledStart(appointment)
                .minusSeconds(policies.forAppointment(appointment).rescheduleCutoffMinutes() * 60L)))
            requireActionReason(request.getNotes());
        var changePolicy = policies.forAppointment(appointment);
        if (!canManageAppointments() && changePolicy.maxReschedules() != null && changePolicy.maxReschedules() > 0
                && history.countByAppointmentIdAndActorUserIdAndAction(appointment.getId(), appointment.getUserId(), "RESCHEDULE") >= changePolicy.maxReschedules())
            throw new BusinessException("The maximum number of customer reschedules has been reached; contact reception");
        requireBookingHorizon(request.getAppointmentDate(), changePolicy);
        String originalInterval = appointment.getAppointmentDate() + " " + appointment.getStartTime() + "-" + appointment.getEndTime();

        validateDateTimeNotPast(request.getAppointmentDate(), request.getStartTime());
        checkStoreSchedule(request.getAppointmentDate(), request.getStartTime(), null);
        for (AppointmentItem item : appointment.getItems())
            if (item.getTreatmentSessionItemId() != null)
                treatments.validateReschedule(item, request.getAppointmentDate().atTime(request.getStartTime())
                        .atZone(SpaTimeRules.ZONE).toInstant());

        appointment.getItems().stream()
                .map(AppointmentItem::getStaff)
                .filter(Objects::nonNull)
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
                throw new BusinessException("Thiếu thông tin khung giờ hẹn ban đầu; cần kiểm tra đối soát");
            }
            long reservedMinutes = Duration.between(item.getStartTime(), item.getEndTime()).toMinutes();
            if (reservedMinutes <= 0 || reservedMinutes > Duration.between(currentStartTime, calendar.forDate(request.getAppointmentDate()).closesAt()).toMinutes()) {
                throw new BusinessException("Lịch hẹn mới phải nằm trong khung giờ mở cửa của cơ sở");
            }
            validateService(item.getService());
            if (item.getStaff() != null) validateStaff(item.getStaff(), item.getService(), request.getAppointmentDate());
            LocalTime itemEndTime = currentStartTime.plusMinutes(reservedMinutes);
            if (item.getTreatmentSessionItemId() != null) treatments.validateReschedule(item,
                    request.getAppointmentDate().atTime(currentStartTime).atZone(SpaTimeRules.ZONE).toInstant());
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

        checkStoreSchedule(request.getAppointmentDate(), request.getStartTime(), currentStartTime);

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

    private Map<Long, String> resolveStaffNames(List<Appointment> appointmentList) {
        if (appointmentList == null || appointmentList.isEmpty()) return Map.of();
        List<Staff> staffList = appointmentList.stream()
                .filter(a -> a.getItems() != null)
                .flatMap(a -> a.getItems().stream())
                .map(AppointmentItem::getStaff)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (staffList.isEmpty()) return Map.of();
        List<Long> userIds = staffList.stream()
                .map(Staff::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        var userSummaries = identityFacade.findUserSummaries(userIds);
        Map<Long, String> map = new HashMap<>();
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

    private Appointment locked(Long id) {
        treatments.lockForAppointment(id);
        return appointmentRepository.findByIdForUpdate(id).filter(a -> !Boolean.TRUE.equals(a.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
    }
    private Appointment lockedForRead(Long id) { return appointmentRepository.findByIdWithItems(id).filter(a -> !Boolean.TRUE.equals(a.getIsDeleted()))
            .orElseThrow(() -> new ResourceNotFoundException("Appointment not found")); }
    private AppointmentResponse response(Appointment a) {
        UserSummaryDto user = identityFacade.findUserSummaryById(a.getUserId()).orElse(null);
        String customerName = user != null ? user.getFullName() : null;
        String customerPhone = user != null ? user.getPhone() : null;
        var staffNames = resolveStaffNames(List.of(a));
        return AppointmentResponse.of(a, customerName, customerPhone, staffNames);
    }
    private Instant now() { return Instant.now().truncatedTo(ChronoUnit.MICROS); }
    private Instant scheduledStart(Appointment a) { return a.getAppointmentDate().atTime(a.getStartTime()).atZone(SpaTimeRules.ZONE).toInstant(); }
    private Instant scheduledEnd(Appointment a) { return a.getAppointmentDate().atTime(a.getEndTime() != null ? a.getEndTime() : a.getStartTime()).atZone(SpaTimeRules.ZONE).toInstant(); }
    private boolean isAppointmentPast(Appointment a) { return scheduledEnd(a).isBefore(Instant.now()); }
    private void requireCutoff(Appointment a, int minutes) {
        if (!Instant.now().isBefore(scheduledStart(a).minusSeconds(minutes * 60L))) throw new BusinessException("Đã quá thời hạn cho phép hủy hoặc đổi lịch hẹn");
    }
    private void requireActionReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500) throw new BusinessException("Thay đổi phân công chuyên viên cần lý do tối đa 500 ký tự");
    }
    private void requireTicketExpiry(UserServiceTicket ticket, LocalDate date, LocalTime start) {
        Instant expiry = ticket.getExpiryDate();
        if (expiry == null) return;
        Instant serviceTime = date.atTime(start).atZone(SpaTimeRules.ZONE).toInstant();
        if (!serviceTime.isBefore(expiry)) {
            throw new BusinessException("Hạn sử dụng của vé không đủ dài cho thời điểm thực hiện dịch vụ này (" + date + " " + start + ")");
        }
        if (!"SERVICE_START".equals(ticket.getExpiryCheckMode()) && !Instant.now().isBefore(expiry)) {
            throw new BusinessException("Vé liệu trình đã hết hạn sử dụng");
        }
    }
    private void lockItemTickets(List<AppointmentItem> items) {
        var locked = lockTicketsInOrder(items);
        items.stream().filter(i -> i.getTicket() != null).forEach(i -> i.setTicket(locked.get(i.getTicket().getId())));
    }
    private void appendHistory(Appointment a, AppointmentItem item, String action, String from, String to, String details) {
        var row = new AppointmentActionHistory();
        row.setAppointmentId(a.getId()); row.setAppointmentItemId(item == null ? null : item.getId());
        row.setActorUserId(SecurityUtils.getCurrentUserIdOptional().orElse(null)); row.setAction(action);
        row.setFromState(from != null && from.length() <= 40 ? from : null); row.setToState(to);
        row.setDetails(details); history.save(row);
        if(customerInbox!=null && row.getId()!=null && Set.of("BOOK","ASSIGN_STAFF","REASSIGN_STAFF","CHANGE_STATUS","CANCEL","EXPIRE_PENDING","RESCHEDULE","CHECK_IN","ADD_IN_SESSION_ITEM").contains(action)) {
            String title=switch(action) {
                case "BOOK" -> "Đã tiếp nhận yêu cầu đặt lịch Spa";
                case "RESCHEDULE" -> "Lịch hẹn Spa đã đổi giờ";
                case "CANCEL","EXPIRE_PENDING" -> "Lịch hẹn Spa đã hủy";
                case "CHECK_IN" -> "Đã ghi nhận khách đến Spa";
                case "REASSIGN_STAFF" -> "Đã điều chuyển chuyên viên phục vụ";
                case "ADD_IN_SESSION_ITEM" -> "Đã thêm dịch vụ phát sinh tại chỗ";
                default -> "Lịch hẹn Spa đã cập nhật";
            };
            customerInbox.publish(a.getUserId(),"appointment:"+a.getId()+":history:"+row.getId(),"SPA",title,
                    "Lịch #"+a.getId()+" · "+a.getAppointmentDate()+" "+a.getStartTime()+" (giờ Việt Nam). Trạng thái: "+a.getStatus().name()+".",
                    "profile?tab=appointments&appointment="+a.getId());

            // Đồng thời gửi thông báo tới chuyên viên/KTV được phân công trong lịch hẹn
            if (a.getItems() != null) {
                java.util.Set<Long> notifiedStaffUserIds = new java.util.HashSet<>();
                for (AppointmentItem ai : a.getItems()) {
                    if (ai.getStaff() != null && ai.getStaff().getUserId() != null
                            && !ai.getStaff().getUserId().equals(a.getUserId())
                            && notifiedStaffUserIds.add(ai.getStaff().getUserId())) {
                        customerInbox.publish(ai.getStaff().getUserId(),
                                "appointment:" + a.getId() + ":staff:" + ai.getStaff().getUserId() + ":history:" + row.getId(),
                                "SPA_STAFF",
                                "Lịch phục vụ Spa: " + title,
                                "Lịch hẹn #" + a.getId() + " (" + ai.getServiceNameSnapshot() + ") · " + a.getAppointmentDate() + " " + a.getStartTime() + ". Trạng thái: " + a.getStatus().name() + ".",
                                "staff/appointments/" + a.getId());
                    }
                }
            }
        }
    }

    private Map<Long, UserServiceTicket> lockTicketsInOrder(List<AppointmentItem> items) {
        Map<Long, UserServiceTicket> locked = new HashMap<>();
        items.stream()
                .map(AppointmentItem::getTicket)
                .filter(Objects::nonNull)
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
            throw new BusinessException("Dịch vụ hiện không khả dụng");
        }
    }

    private void validateStaff(Staff staff, BeautyService service, LocalDate date) {
        if (!Boolean.TRUE.equals(staff.getIsActive()) || Boolean.TRUE.equals(staff.getIsDeleted())
                || staff.getSkills() == null || staff.getSkills().stream().noneMatch(skill ->
                    !Boolean.TRUE.equals(skill.getIsDeleted()) && skill.getService().getId().equals(service.getId())
                    && (!Boolean.TRUE.equals(service.getRequiresCertification()) || (Boolean.TRUE.equals(skill.getIsCertified())
                    && (skill.getCertifiedUntil()==null || !skill.getCertifiedUntil().isBefore(date)))))) {
            throw new BusinessException("Chuyên viên không đủ chuyên môn hoặc không khả dụng cho dịch vụ này");
        }
    }

    private LocalTime serviceEnd(LocalTime start, BeautyService service) {
        return SpaTimeRules.end(start, service);
    }

    private void validateDateTimeNotPast(LocalDate appointmentDate, LocalTime startTime) {
        SpaTimeRules.requireFuture(appointmentDate, startTime);

    }

    private void requireBookingHorizon(LocalDate date, SpaBookingPolicyService.Policy policy) {
        if (policy != null && policy.maxAdvanceDays() != null && policy.maxAdvanceDays() > 0
                && date != null && date.isAfter(LocalDate.now(SpaTimeRules.ZONE).plusDays(policy.maxAdvanceDays())))
            throw new BusinessException("The requested date exceeds the booking horizon");
    }

    private void checkStoreSchedule(LocalDate date, LocalTime startTime, LocalTime endTime) {
        calendar.requireOpen(date,startTime,endTime==null ? startTime.plusSeconds(1) : endTime);
    }

    private void requireWorkingShift(Staff staff, LocalDate date, LocalTime start, LocalTime end) {
        calendar.requireOpen(date,start,end);
        if (!schedules.coversWorkingInterval(staff.getId(), date, start, end))
            throw new BusinessException("Chuyên viên chưa được phân ca bao quát toàn bộ thời gian dịch vụ");
    }

    private boolean canManageAppointments() { return access.canManageReception(); }

    @Override @Transactional(readOnly=true)
    public List<String> availableSlots(Long id, LocalDate date) {
        Appointment appointment = appointmentRepository.findByIdWithItems(id)
                .filter(a -> !Boolean.TRUE.equals(a.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch hẹn"));
        access.requireCanViewAppointment(appointment);
        if (!Objects.equals(appointment.getUserId(), SecurityUtils.getCurrentUserId()) && !canManageAppointments())
            throw new AccessDeniedException("Không có quyền đổi lịch hẹn này");
        if (!Set.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED).contains(appointment.getStatus()) || appointment.getCheckedInAt() != null)
            throw new BusinessException("Lịch hẹn này không còn được đổi giờ");
        if (date == null || date.isBefore(LocalDate.now(SpaTimeRules.ZONE))) throw new BusinessException("Ngày hẹn không hợp lệ");
        requireBookingHorizon(date, policies.forAppointment(appointment));
        var hours = calendar.forDate(date); if (hours.closed()) return List.of();
        var result = new ArrayList<String>();
        for (int minute = hours.opensAt().getHour() * 60 + hours.opensAt().getMinute(); minute < hours.closesAt().getHour() * 60 + hours.closesAt().getMinute(); minute += 30) {
            LocalTime start = LocalTime.of(minute / 60, minute % 60), cursor = start;
            if (!date.atTime(start).isAfter(LocalDateTime.now(SpaTimeRules.ZONE))) continue;
            boolean valid = true;
            for (AppointmentItem item : appointment.getItems()) {
                long duration = Duration.between(item.getStartTime(), item.getEndTime()).toMinutes();
                if (duration <= 0 || duration > Duration.between(cursor, hours.closesAt()).toMinutes()) { valid = false; break; }
                LocalTime end=cursor.plusMinutes(duration), itemStart=cursor;
                try {
                    validateService(item.getService());
                    if(item.getTicket()!=null) requireTicketExpiry(item.getTicket(),date,cursor);
                    if(item.getTreatmentSessionItemId()!=null) treatments.validateReschedule(item,date.atTime(cursor).atZone(SpaTimeRules.ZONE).toInstant());
                    List<Staff> candidates=item.getStaff()==null ? staffRepository.findQualifiedActiveStaff(item.getService().getId()) : List.of(item.getStaff());
                    boolean available=candidates.stream().anyMatch(person -> {
                        try { validateStaff(person,item.getService(),date); }
                        catch(BusinessException unavailable) { return false; }
                        return schedules.coversWorkingInterval(person.getId(),date,itemStart,end)
                                && !appointmentRepository.existsOverlappingAppointmentForStaffExcludingAppointment(person.getId(),date,itemStart,end,id);
                    });
                    if(!available || !facilities.hasAvailability(item.getService().getId(),date,cursor,end,id)) { valid=false; break; }
                } catch(BusinessException unavailable) { valid=false; break; }
                cursor=end;
            }
            if(valid && !appointmentRepository.existsOverlappingAppointmentForUserExcluding(appointment.getUserId(),id,date,start,cursor)) result.add(start.toString());
        }
        return result;
    }
}
