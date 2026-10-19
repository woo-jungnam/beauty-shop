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
    @Mock TreatmentAppointmentBridge treatments;
    @Mock SpaBusinessCalendarService calendar;
    @Mock SpaConsumableService materials;
    @Mock SpaFinancialService finance;
    @Mock com.core.beautyshop.modules.notification.api.CustomerNotificationFacade customerInbox;
    AppointmentServiceImpl booking;
    BeautyServiceServiceImpl availability;

    @BeforeEach void setup() {
        booking = new AppointmentServiceImpl(appointments, services, staff, tickets, identity, orders, shifts, new SpaAccessService(), preparation, facilities, policies, new TicketUsageService(movements), history, treatments, calendar, materials, finance);
        availability = new BeautyServiceServiceImpl(services, packages, staff, appointments, identity, shifts, facilities, calendar);
        org.springframework.test.util.ReflectionTestUtils.setField(booking,"customerInbox",customerInbox);
        lenient().when(calendar.forDate(any())).thenAnswer(call -> new SpaBusinessCalendarService.Window(call.getArgument(0),false,SpaTimeRules.OPEN,SpaTimeRules.CLOSE,null));
        lenient().when(facilities.hasAvailability(anyLong(), any(), any(), any())).thenReturn(true);
        lenient().when(policies.forAppointment(any())).thenReturn(new SpaBookingPolicyService.Policy("test",0,0,0,0,"FORFEIT"));
        lenient().when(shifts.hasScheduleOnDate(anyLong(), any())).thenReturn(true);
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

    @Test void treatmentVisitCannotUseReceptionShortcutToManufacturePerformance() {
        var value = appointment(service(), null);
        value.getItems().getFirst().setTreatmentSessionItemId(900L);
        when(appointments.findByIdForUpdate(10L)).thenReturn(Optional.of(value));
        var request = new UpdateAppointmentStatusRequest(); request.setStatus(AppointmentStatus.COMPLETED);
        var failure = assertThrows(BusinessException.class, () -> booking.updateAppointmentStatus(10L, request));
        assertTrue(failure.getMessage().contains("không hợp lệ"));
        assertNull(value.getCheckedInAt());
        assertEquals(com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.PLANNED,
                value.getItems().getFirst().getExecutionStatus());
        verifyNoInteractions(tickets, movements);
    }

    @Test void ordinaryVisitCannotFinishPlannedItemsOrManufactureCustomerConsent() {
        var value=appointment(service(),null);value.setStatus(AppointmentStatus.IN_PROGRESS);
        when(appointments.findByIdForUpdate(10L)).thenReturn(Optional.of(value));
        var request=new UpdateAppointmentStatusRequest();request.setStatus(AppointmentStatus.COMPLETED);
        assertThrows(BusinessException.class,()->booking.updateAppointmentStatus(10L,request));
        assertNull(value.getCheckedInAt());
        assertEquals(com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.PLANNED,value.getItems().getFirst().getExecutionStatus());
        verifyNoInteractions(tickets,movements);verify(appointments,never()).save(any());
    }

    @Test void reschedulingNotifiesOnlyTheOwnerWithPublicScheduleAndNoInternalReason() {
        var value=appointment(service(),null);when(appointments.findByIdForUpdate(10L)).thenReturn(Optional.of(value));
        when(appointments.save(any())).thenAnswer(call->call.getArgument(0));
        when(history.save(any())).thenAnswer(call->{AppointmentActionHistory row=call.getArgument(0);row.setId(123L);return row;});
        var request=new RescheduleAppointmentRequest();request.setAppointmentDate(tomorrow().plusDays(1));request.setStartTime(LocalTime.of(14,0));request.setNotes("PRIVATE_INTERNAL_REASON");
        booking.rescheduleAppointment(10L,request);
        var body=ArgumentCaptor.forClass(String.class);
        verify(customerInbox).publish(eq(50L),eq("appointment:10:history:123"),eq("SPA"),anyString(),body.capture(),eq("profile?tab=appointments&appointment=10"));
        assertTrue(body.getValue().contains(request.getAppointmentDate().toString()));
        assertFalse(body.getValue().contains("PRIVATE_INTERNAL_REASON"));
    }

    @Test void missingShiftNeverDefaultsToFullDayAvailability() {
        when(services.findWithCategoryById(1L)).thenReturn(Optional.of(service()));
        when(staff.findQualifiedActiveStaff(1L)).thenReturn(List.of(technician(service(),5L)));
        assertTrue(availability.getAvailableSlots(1L,tomorrow(),5L).isEmpty());
    }

    @Test void certificationIsEnforcedForTheAppointmentDateWhenServiceRequiresIt() {
        var service=service();service.setRequiresCertification(true);var person=technician(service,6L);
        person.getSkills().getFirst().setIsCertified(true);person.getSkills().getFirst().setCertifiedUntil(tomorrow().minusDays(1));
        var value=appointment(service,null);when(appointments.findByIdForUpdate(10L)).thenReturn(Optional.of(value));
        when(staff.findByIdWithLock(6L)).thenReturn(Optional.of(person));
        var request=new UpdateAppointmentStatusRequest();request.setStatus(AppointmentStatus.CONFIRMED);request.setStaffAssignments(Map.of(20L,6L));
        assertThrows(BusinessException.class,()->booking.updateAppointmentStatus(10L,request));
        verify(appointments,never()).save(any());
    }

    @Test void bookingChecksCourseOwnershipBeforeLockingTicketsEvenForLegacyRequest() {
        when(identity.getUserSummaryById(50L)).thenReturn(UserSummaryDto.builder().id(50L).build());
        var item = new AppointmentItemRequest(); item.setServiceId(1L); item.setTicketId(88L);
        var request = new BookAppointmentRequest(); request.setAppointmentDate(tomorrow());
        request.setStartTime(LocalTime.of(10, 0)); request.setItems(List.of(item));
        doThrow(new BusinessException("Vé thuộc hồ sơ điều trị, cần đặt đúng buổi"))
                .when(treatments).validateAndLockBooking(isNull(), eq(50L), eq(1L), eq(88L), any());
        assertThrows(BusinessException.class, () -> booking.bookAppointment(request));
        verifyNoInteractions(tickets, services, staff);
        verify(appointments, never()).saveAndFlush(any());
    }

    @Test void duplicateTreatmentItemIsRejectedBeforeAnyQuotaIsReserved() {
        when(identity.getUserSummaryById(50L)).thenReturn(UserSummaryDto.builder().id(50L).build());
        var item = new AppointmentItemRequest(); item.setServiceId(1L); item.setTreatmentSessionItemId(900L);
        var request = new BookAppointmentRequest(); request.setAppointmentDate(tomorrow());
        request.setStartTime(LocalTime.of(10, 0)); request.setItems(List.of(item, item));
        assertThrows(BusinessException.class, () -> booking.bookAppointment(request));
        verifyNoInteractions(treatments, tickets, services, movements);
    }

    @Test void staffCannotFinishTreatmentAssignedToAnotherPerformer() {
        var service = service(); var value = appointment(service, technician(service, 5L));
        value.setStatus(AppointmentStatus.IN_PROGRESS);
        var item = value.getItems().getFirst(); item.setTreatmentSessionItemId(900L);
        item.setExecutionStatus(com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.IN_PROGRESS);
        when(appointments.findByIdForUpdate(10L)).thenReturn(Optional.of(value));
        var principal = new UserDetailsImpl(999L, "other-staff", "staff@example.test", "password",
                List.of(new SimpleGrantedAuthority("ROLE_STAFF")));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        var request = new ExecuteAppointmentItemRequest();
        request.setStatus(com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.PERFORMED);
        request.setNotes("Attempted finish by another staff member");
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> booking.executeItem(10L, 20L, request));
        verifyNoInteractions(tickets, movements);
    }

    @Test void interruptedTreatmentRequiresReasonAndReleasesReservationWithoutFakingPerformance() {
        var value = appointment(service(), null); value.setStatus(AppointmentStatus.IN_PROGRESS);
        var item = value.getItems().getFirst(); item.setTreatmentSessionItemId(900L);
        item.setExecutionStatus(com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.IN_PROGRESS);
        item.setActualStartedAt(Instant.now().minusSeconds(30));
        when(appointments.findByIdForUpdate(10L)).thenReturn(Optional.of(value));
        var request = new ExecuteAppointmentItemRequest();
        request.setStatus(com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.SKIPPED);
        assertThrows(BusinessException.class, () -> booking.executeItem(10L, 20L, request));
        request.setNotes("Dừng theo đánh giá của người phụ trách do khách phản ứng");
        booking.executeItem(10L, 20L, request);
        assertEquals(com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus.SKIPPED, item.getExecutionStatus());
        assertNotNull(item.getActualCompletedAt());
        verify(treatments).synchronizeExecution(item);
        verifyNoInteractions(movements);
    }
}
