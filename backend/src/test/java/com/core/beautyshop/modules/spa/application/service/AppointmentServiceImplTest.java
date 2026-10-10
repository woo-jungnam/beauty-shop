package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.identity.api.IdentityFacade;
import com.core.beautyshop.modules.order.api.OrderFacade;
import com.core.beautyshop.modules.identity.api.dto.UserSummaryDto;
import com.core.beautyshop.modules.spa.application.dto.request.AppointmentItemRequest;
import com.core.beautyshop.modules.spa.application.dto.request.BookAppointmentRequest;
import com.core.beautyshop.modules.spa.application.dto.response.AppointmentResponse;
import com.core.beautyshop.modules.spa.application.service.impl.AppointmentServiceImpl;
import com.core.beautyshop.modules.spa.domain.Appointment;
import com.core.beautyshop.modules.spa.domain.AppointmentItem;
import com.core.beautyshop.modules.spa.domain.AppointmentRepository;
import com.core.beautyshop.modules.spa.domain.BeautyService;
import com.core.beautyshop.modules.spa.domain.BeautyServiceRepository;
import com.core.beautyshop.modules.spa.domain.ServicePackage;
import com.core.beautyshop.modules.spa.domain.ServicePackageItem;
import com.core.beautyshop.modules.spa.domain.Staff;
import com.core.beautyshop.modules.spa.domain.StaffRepository;
import com.core.beautyshop.modules.spa.domain.UserServiceTicket;
import com.core.beautyshop.modules.spa.domain.UserServiceTicketRepository;
import com.core.beautyshop.modules.spa.domain.StaffScheduleRepository;
import com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus;
import com.core.beautyshop.modules.spa.domain.enums.TicketStatus;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.security.services.UserDetailsImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceImplTest {

    @Test
    void cancelledAppointmentCannotBeReopenedOrRestoreAnotherSession() {
        var principal = new UserDetailsImpl(USER_ID,"admin","admin@example.test","password", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
        Appointment appointment = Appointment.builder().userId(USER_ID)
                .status(AppointmentStatus.CANCELLED).items(new ArrayList<>()).build();
        when(appointmentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(appointment));
        var request = new com.core.beautyshop.modules.spa.application.dto.request.UpdateAppointmentStatusRequest();
        request.setStatus(AppointmentStatus.CONFIRMED);
        assertThrows(BusinessException.class, () -> appointmentService.updateAppointmentStatus(100L, request));
        verifyNoInteractions(ticketRepository);
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void serviceCannotConsumeAnotherServicesRemainingQuota() {
        BeautyService service = BeautyService.builder().name("A").durationMinutes(30).preparationTimeMinutes(0)
                .basePrice(BigDecimal.TEN).build();
        service.setId(1L);
        UserServiceTicket ticket = UserServiceTicket.builder().userId(USER_ID).orderId(500L)
                .totalSessions(4).usedSessions(1).build();
        ticket.setId(10L);
        ticket.getEntitlements().put(1L, new com.core.beautyshop.modules.spa.domain.TicketEntitlement(1, 1));
        ticket.getEntitlements().put(2L, new com.core.beautyshop.modules.spa.domain.TicketEntitlement(3, 0));
        AppointmentItemRequest item = new AppointmentItemRequest(); item.setServiceId(1L); item.setTicketId(10L);
        BookAppointmentRequest request = new BookAppointmentRequest();
        request.setAppointmentDate(LocalDate.now().plusDays(1)); request.setStartTime(LocalTime.of(10, 0));
        request.setItems(List.of(item));
        when(identityFacade.getUserSummaryById(USER_ID)).thenReturn(UserSummaryDto.builder().id(USER_ID).build());
        when(beautyServiceRepository.findAvailableByIdForUpdate(1L)).thenReturn(Optional.of(service));
        when(ticketRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(ticket));
        when(orderFacade.isPaidOrderForUser(500L, USER_ID)).thenReturn(true);
        assertThrows(BusinessException.class, () -> appointmentService.bookAppointment(request));
        assertEquals(1, ticket.getUsedSessions());
        verify(appointmentRepository, never()).save(any());
    }

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private BeautyServiceRepository beautyServiceRepository;

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private UserServiceTicketRepository ticketRepository;

    @Mock
    private IdentityFacade identityFacade;

    @Mock
    private OrderFacade orderFacade;

    @Mock
    private StaffScheduleRepository schedules;

    @Mock private com.core.beautyshop.modules.spa.application.service.SpaPreparationService preparation;
    @Mock private com.core.beautyshop.modules.spa.application.service.FacilitySchedulingService facilities;
    @Mock private com.core.beautyshop.modules.spa.application.service.SpaBookingPolicyService policies;
    @Mock private com.core.beautyshop.modules.spa.domain.AppointmentActionHistoryRepository history;
    @Mock private com.core.beautyshop.modules.spa.domain.TicketSessionMovementRepository movements;
    private AppointmentServiceImpl appointmentService;

    private static final Long USER_ID = 50L;

    @BeforeEach
    void setUpSecurity() {
        appointmentService = new AppointmentServiceImpl(appointmentRepository, beautyServiceRepository, staffRepository, ticketRepository,
                identityFacade, orderFacade, schedules, new com.core.beautyshop.modules.spa.application.service.SpaAccessService(),
                preparation, facilities, policies, new com.core.beautyshop.modules.spa.application.service.TicketUsageService(movements), history);
        lenient().when(policies.forAppointment(any())).thenReturn(new com.core.beautyshop.modules.spa.application.service.SpaBookingPolicyService.Policy("test",0,0,0,0,"FORFEIT"));
        lenient().when(schedules.coversWorkingInterval(anyLong(), any(), any(), any())).thenReturn(true);
        UserDetailsImpl userDetails = new UserDetailsImpl(
                USER_ID,
                "customer1",
                "customer1@beautyshop.com",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testBookAppointment_WithTicket_ReservesSessionWithoutConsumingIt() {
        BeautyService service = BeautyService.builder()
                .name("Chăm sóc da mặt cơ bản")
                .basePrice(new BigDecimal("300000"))
                .durationMinutes(45)
                .preparationTimeMinutes(15)
                .build();
        service.setId(1L);

        UserServiceTicket ticket = UserServiceTicket.builder()
                .userId(USER_ID)
                .orderId(500L)
                .totalSessions(5)
                .usedSessions(0)
                .expiryDate(Instant.now().plus(60, ChronoUnit.DAYS))
                .status(TicketStatus.ACTIVE)
                .build();
        ticket.setId(10L);
        ticket.getEntitlements().put(1L, new com.core.beautyshop.modules.spa.domain.TicketEntitlement(5, ticket.getUsedSessions()));

        AppointmentItemRequest itemRequest = new AppointmentItemRequest();
        itemRequest.setServiceId(1L);
        itemRequest.setTicketId(10L);

        BookAppointmentRequest request = new BookAppointmentRequest();
        request.setAppointmentDate(LocalDate.now().plusDays(1));
        request.setStartTime(LocalTime.of(10, 0));
        request.setNotes("Hen cuoi tuan");
        request.setItems(List.of(itemRequest));

        UserSummaryDto userSummary = UserSummaryDto.builder()
                .id(USER_ID)
                .fullName("Khách Hàng A")
                .build();

        when(identityFacade.getUserSummaryById(USER_ID)).thenReturn(userSummary);
        when(beautyServiceRepository.findAvailableByIdForUpdate(1L)).thenReturn(Optional.of(service));
        when(ticketRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(ticket));
        when(orderFacade.isPaidOrderForUser(500L, USER_ID)).thenReturn(true);
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(inv -> {
            Appointment apt = inv.getArgument(0);
            apt.setId(100L);
            apt.getItems().getFirst().setId(200L);
            return apt;
        });

        AppointmentResponse response = appointmentService.bookAppointment(request);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals("Khách Hàng A", response.getCustomerName());
        assertEquals(1, response.getItems().size());
        assertEquals(BigDecimal.ZERO, response.getItems().get(0).getPrice());
        assertEquals(10L, response.getItems().get(0).getTicketId());
        assertTrue(response.getItems().get(0).getIsTicketUsed());

        assertEquals(0, ticket.getUsedSessions());
        assertEquals(1, ticket.getReservedSessions());
        assertEquals(1, ticket.getEntitlements().get(1L).getReserved());
        verify(ticketRepository, times(2)).findByIdForUpdate(10L);

        verify(appointmentRepository).saveAndFlush(any(Appointment.class));
    }

    @Test
    void testBookAppointment_LegacyTicketWithoutPaidOrder_ThrowsBusinessException() {
        BeautyService service = BeautyService.builder()
                .name("Chăm sóc da")
                .basePrice(new BigDecimal("300000"))
                .durationMinutes(45)
                .preparationTimeMinutes(15)
                .build();
        service.setId(1L);

        UserServiceTicket legacyTicket = UserServiceTicket.builder()
                .userId(USER_ID)
                .totalSessions(5)
                .usedSessions(0)
                .expiryDate(Instant.now().plus(60, ChronoUnit.DAYS))
                .status(TicketStatus.ACTIVE)
                .build();
        legacyTicket.setId(10L);

        AppointmentItemRequest itemRequest = new AppointmentItemRequest();
        itemRequest.setServiceId(1L);
        itemRequest.setTicketId(10L);

        BookAppointmentRequest request = new BookAppointmentRequest();
        request.setAppointmentDate(LocalDate.now().plusDays(1));
        request.setStartTime(LocalTime.of(10, 0));
        request.setItems(List.of(itemRequest));

        when(identityFacade.getUserSummaryById(USER_ID)).thenReturn(
                UserSummaryDto.builder().id(USER_ID).fullName("Khách hàng A").build());
        when(beautyServiceRepository.findAvailableByIdForUpdate(1L)).thenReturn(Optional.of(service));
        when(ticketRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(legacyTicket));

        assertThrows(BusinessException.class, () -> appointmentService.bookAppointment(request));

        verifyNoInteractions(orderFacade);
        verify(ticketRepository, never()).save(any());
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void testCancelAppointment_RestoresTicketSession() {
        UserServiceTicket ticket = UserServiceTicket.builder()
                .userId(USER_ID)
                .totalSessions(5)
                .usedSessions(1).reservedSessions(1)
                .expiryDate(Instant.now().plus(60, ChronoUnit.DAYS))
                .status(TicketStatus.ACTIVE)
                .build();
        ticket.setId(10L);
        ticket.getEntitlements().put(1L, new com.core.beautyshop.modules.spa.domain.TicketEntitlement(5, 1, 1));

        BeautyService service = BeautyService.builder().name("Service").build();
        service.setId(1L);
        AppointmentItem item = AppointmentItem.builder()
                .service(service)
                .ticket(ticket)
                .ticketUsageState(com.core.beautyshop.modules.spa.domain.enums.TicketUsageState.RESERVED)
                .price(BigDecimal.ZERO)
                .build();

        item.setId(200L);
        Appointment appointment = Appointment.builder()
                .userId(USER_ID)
                .appointmentDate(LocalDate.now().plusDays(2))
                .startTime(LocalTime.of(14, 0))
                .status(AppointmentStatus.PENDING)
                .items(new ArrayList<>(List.of(item)))
                .build();
        appointment.setId(100L);

        when(appointmentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(appointment));
        when(ticketRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(ticket));

        appointmentService.cancelAppointment(100L);

        assertEquals(AppointmentStatus.CANCELLED, appointment.getStatus());
        assertEquals(1, ticket.getUsedSessions());
        assertEquals(0, ticket.getReservedSessions());

        verify(appointmentRepository).save(appointment);
    }

    @Test
    void testCancelAppointment_PastAppointment_ThrowsException() {
        Appointment appointment = Appointment.builder()
                .userId(USER_ID)
                .appointmentDate(LocalDate.now().minusDays(1))
                .startTime(LocalTime.of(10, 0))
                .status(AppointmentStatus.PENDING)
                .items(new ArrayList<>())
                .build();
        appointment.setId(100L);

        when(appointmentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(appointment));

        assertThrows(BusinessException.class, () -> appointmentService.cancelAppointment(100L));
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void testBookAppointment_ServiceNotInTicketPackage_ThrowsException() {
        BeautyService serviceA = BeautyService.builder()
                .name("Chăm sóc da mặt")
                .basePrice(new BigDecimal("300000"))
                .durationMinutes(45)
                .preparationTimeMinutes(15)
                .build();
        serviceA.setId(1L);

        BeautyService serviceB = BeautyService.builder()
                .name("Massage Body")
                .basePrice(new BigDecimal("500000"))
                .durationMinutes(60)
                .preparationTimeMinutes(15)
                .build();
        serviceB.setId(2L);

        ServicePackage pkg = ServicePackage.builder()
                .name("Gói Massage Body")
                .items(List.of(ServicePackageItem.builder().service(serviceB).quantity(5).build()))
                .build();

        UserServiceTicket ticket = UserServiceTicket.builder()
                .userId(USER_ID)
                .orderId(501L)
                .servicePackage(pkg)
                .totalSessions(5)
                .usedSessions(0)
                .expiryDate(Instant.now().plus(60, ChronoUnit.DAYS))
                .status(TicketStatus.ACTIVE)
                .build();
        ticket.setId(10L);

        AppointmentItemRequest itemRequest = new AppointmentItemRequest();
        itemRequest.setServiceId(1L);
        itemRequest.setTicketId(10L);

        BookAppointmentRequest request = new BookAppointmentRequest();
        request.setAppointmentDate(LocalDate.now().plusDays(1));
        request.setStartTime(LocalTime.of(10, 0));
        request.setItems(List.of(itemRequest));

        UserSummaryDto userSummary = UserSummaryDto.builder()
                .id(USER_ID)
                .fullName("Khách Hàng A")
                .build();

        when(identityFacade.getUserSummaryById(USER_ID)).thenReturn(userSummary);
        when(beautyServiceRepository.findAvailableByIdForUpdate(1L)).thenReturn(Optional.of(serviceA));
        when(ticketRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(ticket));
        when(orderFacade.isPaidOrderForUser(501L, USER_ID)).thenReturn(true);

        assertThrows(BusinessException.class, () -> appointmentService.bookAppointment(request));
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void testBookAppointment_EmptyItems_ThrowsBusinessException() {
        BookAppointmentRequest request = new BookAppointmentRequest();
        request.setAppointmentDate(LocalDate.now().plusDays(1));
        request.setStartTime(LocalTime.of(10, 0));
        request.setItems(List.of());

        assertThrows(BusinessException.class, () -> appointmentService.bookAppointment(request));
        verifyNoInteractions(identityFacade, appointmentRepository, ticketRepository);
    }

    @Test
    void testRescheduleAppointment_NoShow_ThrowsBusinessException() {
        Appointment appointment = Appointment.builder()
                .userId(USER_ID)
                .appointmentDate(LocalDate.now().minusDays(1))
                .startTime(LocalTime.of(10, 0))
                .status(AppointmentStatus.NO_SHOW)
                .items(new ArrayList<>())
                .build();
        appointment.setId(100L);

        when(appointmentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(appointment));

        com.core.beautyshop.modules.spa.application.dto.request.RescheduleAppointmentRequest request =
                new com.core.beautyshop.modules.spa.application.dto.request.RescheduleAppointmentRequest();
        request.setAppointmentDate(LocalDate.now().plusDays(2));
        request.setStartTime(LocalTime.of(10, 0));

        assertThrows(BusinessException.class, () -> appointmentService.rescheduleAppointment(100L, request));
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void testRescheduleAppointment_PastDate_ThrowsException() {
        Appointment appointment = Appointment.builder()
                .userId(USER_ID)
                .appointmentDate(LocalDate.now().plusDays(2))
                .startTime(LocalTime.of(10, 0))
                .status(AppointmentStatus.PENDING)
                .items(new ArrayList<>())
                .build();
        appointment.setId(100L);

        when(appointmentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(appointment));

        com.core.beautyshop.modules.spa.application.dto.request.RescheduleAppointmentRequest request =
                new com.core.beautyshop.modules.spa.application.dto.request.RescheduleAppointmentRequest();
        request.setAppointmentDate(LocalDate.now().minusDays(1));
        request.setStartTime(LocalTime.of(10, 0));

        assertThrows(BusinessException.class, () -> appointmentService.rescheduleAppointment(100L, request));
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void testRescheduleAppointment_ExceedStoreCloseTime_ThrowsException() {
        BeautyService service = BeautyService.builder()
                .name("Gói Trị Mụn")
                .durationMinutes(60)
                .preparationTimeMinutes(15)
                .build();
        service.setId(1L);

        AppointmentItem item = AppointmentItem.builder()
                .service(service)
                .startTime(LocalTime.of(14, 0))
                .endTime(LocalTime.of(15, 15))
                .build();

        Appointment appointment = Appointment.builder()
                .userId(USER_ID)
                .appointmentDate(LocalDate.now().plusDays(2))
                .startTime(LocalTime.of(14, 0))
                .endTime(LocalTime.of(15, 15))
                .status(AppointmentStatus.PENDING)
                .items(new ArrayList<>(List.of(item)))
                .build();
        appointment.setId(100L);

        when(appointmentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(appointment));

        com.core.beautyshop.modules.spa.application.dto.request.RescheduleAppointmentRequest request =
                new com.core.beautyshop.modules.spa.application.dto.request.RescheduleAppointmentRequest();
        request.setAppointmentDate(LocalDate.now().plusDays(3));
        request.setStartTime(LocalTime.of(19, 30));

        assertThrows(BusinessException.class, () -> appointmentService.rescheduleAppointment(100L, request));
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void testRescheduleAppointment_StaffOverlap_ThrowsException() {
        Staff staff = Staff.builder().userId(200L).build();
        staff.setId(5L);

        BeautyService service = BeautyService.builder()
                .name("Chăm sóc da")
                .durationMinutes(45)
                .preparationTimeMinutes(15)
                .build();
        service.setId(1L);
        staff.setSkills(List.of(com.core.beautyshop.modules.spa.domain.StaffServiceSkill.builder().service(service).build()));

        AppointmentItem item = AppointmentItem.builder()
                .service(service)
                .staff(staff)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .build();

        Appointment appointment = Appointment.builder()
                .userId(USER_ID)
                .appointmentDate(LocalDate.now().plusDays(2))
                .startTime(LocalTime.of(10, 0))
                .status(AppointmentStatus.PENDING)
                .items(new ArrayList<>(List.of(item)))
                .build();
        appointment.setId(100L);

        when(appointmentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(appointment));
        when(staffRepository.findByIdWithLock(5L)).thenReturn(Optional.of(staff));
        when(appointmentRepository.existsOverlappingAppointmentForStaffExcludingAppointment(
                eq(5L), any(LocalDate.class), any(LocalTime.class), any(LocalTime.class), eq(100L)))
                .thenReturn(true);
        when(identityFacade.findUserSummaryById(200L)).thenReturn(Optional.of(
                UserSummaryDto.builder().id(200L).fullName("Kỹ thuật viên Lan").build()));

        com.core.beautyshop.modules.spa.application.dto.request.RescheduleAppointmentRequest request =
                new com.core.beautyshop.modules.spa.application.dto.request.RescheduleAppointmentRequest();
        request.setAppointmentDate(LocalDate.now().plusDays(3));
        request.setStartTime(LocalTime.of(14, 0));

        assertThrows(BusinessException.class, () -> appointmentService.rescheduleAppointment(100L, request));
        verify(appointmentRepository, never()).save(any());
    }
}
