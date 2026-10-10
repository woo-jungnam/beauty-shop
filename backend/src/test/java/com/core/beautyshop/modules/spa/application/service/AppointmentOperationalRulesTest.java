package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.identity.api.IdentityFacade;
import com.core.beautyshop.modules.identity.api.dto.UserSummaryDto;
import com.core.beautyshop.modules.order.api.OrderFacade;
import com.core.beautyshop.modules.spa.application.dto.request.*;
import com.core.beautyshop.modules.spa.application.service.impl.AppointmentServiceImpl;
import com.core.beautyshop.modules.spa.application.service.impl.BeautyServiceServiceImpl;
import com.core.beautyshop.modules.spa.domain.*;
import com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.security.services.UserDetailsImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class AppointmentOperationalRulesTest {
    @Mock AppointmentRepository appointments;
    @Mock BeautyServiceRepository services;
    @Mock StaffRepository staff;
    @Mock UserServiceTicketRepository tickets;
    @Mock IdentityFacade identity;
    @Mock OrderFacade orders;
    @Mock StaffScheduleRepository shifts;
    @Mock ServicePackageRepository packages;
    @Mock SpaPreparationService preparation;
    @Mock FacilitySchedulingService facilities;
    @Mock SpaBookingPolicyService policies;
    @Mock AppointmentActionHistoryRepository history;
    @Mock TicketSessionMovementRepository movements;
    AppointmentServiceImpl booking;
    BeautyServiceServiceImpl availability;

    @BeforeEach void setup() {
        booking = new AppointmentServiceImpl(appointments, services, staff, tickets, identity, orders, shifts, new SpaAccessService(), preparation, facilities, policies, new TicketUsageService(movements), history);
        availability = new BeautyServiceServiceImpl(services, packages, staff, appointments, identity, shifts, facilities);
        lenient().when(facilities.hasAvailability(anyLong(), any(), any(), any())).thenReturn(true);
        lenient().when(policies.forAppointment(any())).thenReturn(new SpaBookingPolicyService.Policy("test",0,0,0,0,"FORFEIT"));
        var principal = new UserDetailsImpl(50L, "customer", "customer@example.test", "password",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    LocalDate tomorrow() { return LocalDate.now(SpaTimeRules.ZONE).plusDays(1); }
    BeautyService service() {
        var value = BeautyService.builder().name("Facial").slug("facial").durationMinutes(45)
                .preparationTimeMinutes(15).basePrice(BigDecimal.valueOf(300000)).build();
        value.setId(1L); return value;
    }
    Staff technician(BeautyService service, long id) {
        var value = Staff.builder().userId(id + 100).build(); value.setId(id);
        value.setSkills(List.of(StaffServiceSkill.builder().service(service).staff(value).build())); return value;
    }
    Appointment appointment(BeautyService service, Staff technician) {
        var item = AppointmentItem.builder().service(service).staff(technician).startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0)).price(service.getBasePrice()).build(); item.setId(20L);
        var value = Appointment.builder().userId(50L).appointmentDate(tomorrow()).startTime(item.getStartTime())
                .endTime(item.getEndTime()).status(AppointmentStatus.CONFIRMED).items(new ArrayList<>(List.of(item))).build();
        value.setId(10L); item.setAppointment(value); return value;
    }
    @Test void noQualifiedStaffMeansNoAvailableSlots() {
        when(services.findWithCategoryById(1L)).thenReturn(Optional.of(service()));
        when(staff.findQualifiedActiveStaff(1L)).thenReturn(List.of());
        assertTrue(availability.getAvailableSlots(1L, tomorrow(), null).isEmpty());
        verifyNoInteractions(appointments, shifts);
    }
    @Test void availabilityRequiresShiftForWholeServiceAndPreparation() {
        var service = service(); var technician = technician(service, 5L);
        when(services.findWithCategoryById(1L)).thenReturn(Optional.of(service));
        when(staff.findQualifiedActiveStaff(1L)).thenReturn(List.of(technician));
        when(shifts.coversWorkingInterval(eq(5L), any(), any(), any())).thenAnswer(call ->
                !((LocalTime) call.getArgument(2)).isBefore(LocalTime.of(10, 0))
                        && !((LocalTime) call.getArgument(3)).isAfter(LocalTime.of(11, 0)));
        assertEquals(List.of("10:00"), availability.getAvailableSlots(1L, tomorrow(), 5L));
    }
    @Test void malformedLongDurationIsRejectedWithoutWrappingTime() {
        var service = service(); service.setDurationMinutes(1500);
        when(services.findWithCategoryById(1L)).thenReturn(Optional.of(service));
        assertThrows(BusinessException.class, () -> availability.getAvailableSlots(1L, tomorrow(), null));
    }
    @Test void bookingOutsideShiftFailsBeforeSavingOrDeductingTicket() {
        var service = service(); var technician = technician(service, 5L);
        when(identity.getUserSummaryById(50L)).thenReturn(UserSummaryDto.builder().id(50L).build());
        when(services.findAvailableByIdForUpdate(1L)).thenReturn(Optional.of(service));
        when(staff.findByIdWithLock(5L)).thenReturn(Optional.of(technician));
        var item = new AppointmentItemRequest(); item.setServiceId(1L); item.setStaffId(5L);
        var request = new BookAppointmentRequest(); request.setAppointmentDate(tomorrow());
        request.setStartTime(LocalTime.of(10, 0)); request.setItems(List.of(item));
        assertThrows(BusinessException.class, () -> booking.bookAppointment(request));
        verify(appointments, never()).save(any()); verifyNoInteractions(tickets);
    }
    @Test void confirmedAppointmentCanChangeTechnicianBeforeAppointmentDay() {
        var service = service(); var replacement = technician(service, 6L);
        var value = appointment(service, technician(service, 5L));
        when(appointments.findByIdForUpdate(10L)).thenReturn(Optional.of(value));
        when(staff.findByIdWithLock(6L)).thenReturn(Optional.of(replacement));
        when(shifts.coversWorkingInterval(6L, tomorrow(), LocalTime.of(10, 0), LocalTime.of(11, 0))).thenReturn(true);
        when(appointments.save(any())).thenAnswer(call -> call.getArgument(0));
        var request = new UpdateAppointmentStatusRequest(); request.setStatus(AppointmentStatus.CONFIRMED);
        request.setStaffAssignments(Map.of(20L, 6L));
        var result = booking.updateAppointmentStatus(10L, request);
        assertEquals(AppointmentStatus.CONFIRMED, result.getStatus());
        assertEquals(6L, result.getItems().getFirst().getStaffId());
    }
    @Test void sameStatusReassignmentStillChecksWorkingShift() {
        var service = service(); var replacement = technician(service, 6L);
        var value = appointment(service, technician(service, 5L));
        when(appointments.findByIdForUpdate(10L)).thenReturn(Optional.of(value));
        when(staff.findByIdWithLock(6L)).thenReturn(Optional.of(replacement));
        var request = new UpdateAppointmentStatusRequest(); request.setStatus(AppointmentStatus.CONFIRMED);
        request.setStaffAssignments(Map.of(20L, 6L));
        assertThrows(BusinessException.class, () -> booking.updateAppointmentStatus(10L, request));
        assertEquals(5L, value.getItems().getFirst().getStaff().getId());
    }
    @Test void cannotStartOrMarkNoShowBeforeAppointmentTime() {
        var value = appointment(service(), null);
        when(appointments.findByIdForUpdate(10L)).thenReturn(Optional.of(value));
        for (var status : List.of(AppointmentStatus.IN_PROGRESS, AppointmentStatus.NO_SHOW)) {
            var request = new UpdateAppointmentStatusRequest(); request.setStatus(status);
            assertThrows(BusinessException.class, () -> booking.updateAppointmentStatus(10L, request));
        }
        verify(appointments, never()).save(any());
    }
    @Test void reschedulingKeepsOriginalDurationAndPriceAfterCatalogChanges() {
        var service = service(); var value = appointment(service, null);
        service.setDurationMinutes(90); service.setPreparationTimeMinutes(30); service.setBasePrice(BigDecimal.valueOf(500000));
        when(appointments.findByIdForUpdate(10L)).thenReturn(Optional.of(value));
        when(appointments.save(any())).thenAnswer(call -> call.getArgument(0));
        var request = new RescheduleAppointmentRequest(); request.setAppointmentDate(tomorrow().plusDays(1));
        request.setStartTime(LocalTime.of(14, 0));
        var result = booking.rescheduleAppointment(10L, request);
        assertEquals(LocalTime.of(15, 0), result.getEndTime());
        assertEquals(BigDecimal.valueOf(300000), result.getItems().getFirst().getPrice());
    }
}
