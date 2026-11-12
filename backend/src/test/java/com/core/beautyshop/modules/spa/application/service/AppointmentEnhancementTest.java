package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.identity.api.IdentityFacade;
import com.core.beautyshop.modules.identity.api.dto.UserSummaryDto;
import com.core.beautyshop.modules.notification.api.CustomerNotificationFacade;
import com.core.beautyshop.modules.order.api.OrderFacade;
import com.core.beautyshop.modules.spa.application.dto.request.AppointmentItemRequest;
import com.core.beautyshop.modules.spa.application.dto.request.UpdateAppointmentStatusRequest;
import com.core.beautyshop.modules.spa.application.dto.response.AppointmentResponse;
import com.core.beautyshop.modules.spa.application.service.impl.AppointmentServiceImpl;
import com.core.beautyshop.modules.spa.domain.*;
import com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus;
import com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus;
import com.core.beautyshop.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentEnhancementTest {

    @Mock private AppointmentRepository appointmentRepository;
    @Mock private BeautyServiceRepository beautyServiceRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private UserServiceTicketRepository ticketRepository;
    @Mock private IdentityFacade identityFacade;
    @Mock private OrderFacade orderFacade;
    @Mock private StaffScheduleRepository schedules;
    @Mock private SpaAccessService access;
    @Mock private SpaPreparationService preparation;
    @Mock private FacilitySchedulingService facilities;
    @Mock private SpaBookingPolicyService policies;
    @Mock private TicketUsageService usage;
    @Mock private AppointmentActionHistoryRepository history;
    @Mock private TreatmentAppointmentBridge treatments;
    @Mock private SpaBusinessCalendarService calendar;
    @Mock private SpaConsumableService materials;
    @Mock private SpaFinancialService finance;
    @Mock private CustomerNotificationFacade customerInbox;

    @InjectMocks
    private AppointmentServiceImpl appointmentService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(appointmentService, "customerInbox", customerInbox);
    }

    @Test
    void canReassignStaffForPlannedItemDuringInProgress() {
        // Arrange
        Long appointmentId = 100L;
        Long itemId = 200L;
        Long oldStaffId = 1L;
        Long newStaffId = 2L;
        LocalDate aptDate = LocalDate.now().plusDays(1);

        Staff oldStaff = Staff.builder().userId(10L).isActive(true).build();
        oldStaff.setId(oldStaffId);
        Staff newStaff = Staff.builder().userId(20L).isActive(true).build();
        newStaff.setId(newStaffId);

        BeautyService service = BeautyService.builder().name("Facial Care").basePrice(BigDecimal.valueOf(300000)).durationMinutes(45).build();
        service.setId(50L);

        StaffServiceSkill skill = StaffServiceSkill.builder()
                .service(service)
                .staff(newStaff)
                .isCertified(true)
                .build();
        skill.setIsDeleted(false);
        newStaff.setSkills(List.of(skill));

        AppointmentItem item = AppointmentItem.builder()
                .service(service)
                .staff(oldStaff)
                .executionStatus(AppointmentItemExecutionStatus.PLANNED)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 45))
                .build();
        item.setId(itemId);

        Appointment appointment = Appointment.builder()
                .userId(99L)
                .status(AppointmentStatus.IN_PROGRESS)
                .appointmentDate(aptDate)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 45))
                .items(new ArrayList<>(List.of(item)))
                .build();
        appointment.setId(appointmentId);

        when(appointmentRepository.findByIdForUpdate(appointmentId)).thenReturn(Optional.of(appointment));
        when(access.canManageReception()).thenReturn(true);
        when(staffRepository.findByIdWithLock(newStaffId)).thenReturn(Optional.of(newStaff));
        when(schedules.coversWorkingInterval(eq(newStaffId), eq(aptDate), any(), any())).thenReturn(true);
        when(appointmentRepository.existsOverlappingAppointmentForStaffExcludingAppointment(eq(newStaffId), eq(aptDate), any(), any(), eq(appointmentId)))
                .thenReturn(false);
        when(identityFacade.findUserSummaryById(99L)).thenReturn(Optional.of(UserSummaryDto.builder().id(99L).fullName("Customer A").phone("0901234567").email("customer@example.com").build()));

        UpdateAppointmentStatusRequest request = new UpdateAppointmentStatusRequest();
        request.setStatus(AppointmentStatus.IN_PROGRESS);
        request.setStaffAssignments(Map.of(itemId, newStaffId));
        request.setNotes("Chuyển KTV mới vì KTV cũ bận việc đột xuất");

        // Act
        AppointmentResponse res = appointmentService.updateAppointmentStatus(appointmentId, request);

        // Assert
        assertNotNull(res);
        assertEquals(newStaff, item.getStaff());
        verify(history, atLeastOnce()).save(argThat(h -> "REASSIGN_STAFF".equals(h.getAction())));
    }

    @Test
    void addInSessionItemSucceedsAndSnapshotsMaterials() {
        // Arrange
        Long appointmentId = 100L;
        LocalDate aptDate = LocalDate.now().plusDays(1);
        BeautyService service = BeautyService.builder().name("Extra Mask").basePrice(BigDecimal.valueOf(150000)).durationMinutes(30).build();
        service.setId(88L);

        AppointmentItem existingItem = AppointmentItem.builder()
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 45))
                .executionStatus(AppointmentItemExecutionStatus.IN_PROGRESS)
                .build();

        Appointment appointment = Appointment.builder()
                .userId(99L)
                .status(AppointmentStatus.IN_PROGRESS)
                .appointmentDate(aptDate)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 45))
                .items(new ArrayList<>(List.of(existingItem)))
                .build();
        appointment.setId(appointmentId);

        when(access.canManageReception()).thenReturn(true);
        when(appointmentRepository.findByIdForUpdate(appointmentId)).thenReturn(Optional.of(appointment));
        when(beautyServiceRepository.findAvailableByIdForUpdate(88L)).thenReturn(Optional.of(service));
        when(calendar.forDate(aptDate)).thenReturn(new SpaBusinessCalendarService.Window(aptDate, false, LocalTime.of(8, 0), LocalTime.of(20, 0), null));
        when(appointmentRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(identityFacade.getUserSummaryById(99L)).thenReturn(UserSummaryDto.builder().id(99L).fullName("Customer A").phone("0901234567").email("customer@example.com").build());

        AppointmentItemRequest addReq = new AppointmentItemRequest();
        addReq.setServiceId(88L);

        // Act
        AppointmentResponse res = appointmentService.addInSessionItem(appointmentId, addReq);

        // Assert
        assertNotNull(res);
        assertEquals(2, appointment.getItems().size());
        AppointmentItem addedItem = appointment.getItems().get(1);
        assertEquals("Extra Mask", addedItem.getServiceNameSnapshot());
        assertEquals(LocalTime.of(10, 45), addedItem.getStartTime());
        assertEquals(LocalTime.of(11, 30), addedItem.getEndTime());
        assertEquals(AppointmentItemExecutionStatus.PLANNED, addedItem.getExecutionStatus());
        verify(materials).snapshotItem(addedItem);
    }

    @Test
    void rateAppointmentUpdatesStaffRating() {
        // Arrange
        Long appointmentId = 100L;
        Long staffId = 5L;
        Long customerId = 99L;

        com.core.beautyshop.shared.security.services.UserDetailsImpl userPrincipal =
                new com.core.beautyshop.shared.security.services.UserDetailsImpl(customerId, "customer", "customer@example.com", "pass", List.of(), 0, null);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(userPrincipal, null, List.of()));

        Staff staff = Staff.builder().userId(50L).rating(4.0).totalReviews(1).isActive(true).build();
        staff.setId(staffId);

        AppointmentItem item = AppointmentItem.builder().staff(staff).build();
        Appointment appointment = Appointment.builder()
                .userId(customerId)
                .status(AppointmentStatus.COMPLETED)
                .items(List.of(item))
                .build();
        appointment.setId(appointmentId);

        when(appointmentRepository.findByIdForUpdate(appointmentId)).thenReturn(Optional.of(appointment));
        when(staffRepository.findByIdWithLock(staffId)).thenReturn(Optional.of(staff));

        // Act: Khách đánh giá 5 sao
        appointmentService.rateAppointment(appointmentId, 5, "Dịch vụ rất tốt");

        // Assert: Rating mới = (4.0 * 1 + 5) / 2 = 4.5, totalReviews = 2
        assertEquals(2, staff.getTotalReviews());
        assertEquals(4.5, staff.getRating());
        verify(staffRepository).save(staff);
        verify(history).save(argThat(h -> "RATE_EXPERIENCE".equals(h.getAction())));

        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void rateAppointmentRejectsDuplicateReview() {
        Long appointmentId = 102L;
        Long customerId = 99L;
        com.core.beautyshop.shared.security.services.UserDetailsImpl userPrincipal =
                new com.core.beautyshop.shared.security.services.UserDetailsImpl(customerId, "customer", "customer@example.com", "pass", List.of(), 0, null);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(userPrincipal, null, List.of()));

        Appointment appointment = Appointment.builder()
                .userId(customerId)
                .status(AppointmentStatus.COMPLETED)
                .items(List.of())
                .build();
        appointment.setId(appointmentId);

        when(appointmentRepository.findByIdForUpdate(appointmentId)).thenReturn(Optional.of(appointment));
        when(history.existsByAppointmentIdAndAction(appointmentId, "RATE_EXPERIENCE")).thenReturn(true);

        assertThrows(BusinessException.class, () -> appointmentService.rateAppointment(appointmentId, 5, "Lần 2"));
        verify(staffRepository, never()).save(any());
        verify(history, never()).save(any());

        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
}

