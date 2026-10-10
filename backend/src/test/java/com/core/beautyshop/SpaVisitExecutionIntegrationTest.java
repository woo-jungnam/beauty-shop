package com.core.beautyshop;

import com.core.beautyshop.modules.spa.application.dto.request.*;
import com.core.beautyshop.modules.spa.application.service.*;
import com.core.beautyshop.modules.spa.domain.*;
import com.core.beautyshop.modules.spa.domain.enums.*;
import com.core.beautyshop.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SpaVisitExecutionIntegrationTest extends SpaConcurrencyIntegrityIntegrationTest {
    @Autowired TicketSessionMovementRepository movements;
    @Autowired AppointmentActionHistoryRepository actions;
    @Autowired AdminSpaTicketService ticketAdmin;
    @Autowired com.core.beautyshop.modules.spa.application.service.impl.AppointmentServiceImpl implementation;
    @Autowired com.core.beautyshop.shared.config.SystemConfigService configs;

    long book(Fixture f, Long ticketId) {
        return as(f.customer(), () -> appointments.bookAppointment(booking(f, f.firstStaffId(), LocalTime.of(10,0), ticketId))).getId();
    }
    void status(Fixture f, long id, AppointmentStatus status, String note) {
        var request = new UpdateAppointmentStatusRequest(); request.setStatus(status); request.setNotes(note);
        as(f.admin(), () -> appointments.updateAppointmentStatus(id, request));
    }
    void item(Fixture f, long id, long itemId, AppointmentItemExecutionStatus status, String note) {
        var request = new ExecuteAppointmentItemRequest(); request.setStatus(status); request.setNotes(note);
        as(f.admin(), () -> appointments.executeItem(id, itemId, request));
    }
    long itemId(long id) { return tx(() -> appointmentRows.findById(id).orElseThrow().getItems().getFirst().getId()); }
    void readyToStart(long id) {
        // A previously checked-in visit carried over from yesterday; no fabricated item performance.
        tx(() -> {
            var a = appointmentRows.findById(id).orElseThrow(); a.setAppointmentDate(LocalDate.now(SpaTimeRules.ZONE).minusDays(1));
            a.setCheckedInAt(Instant.now().minusSeconds(60));
            for (var i : a.getItems()) if (!shifts.coversWorkingInterval(i.getStaff().getId(), a.getAppointmentDate(), LocalTime.of(8,0), LocalTime.of(20,0))) shifts.save(StaffSchedule.builder().staff(i.getStaff()).workDate(a.getAppointmentDate())
                    .startTime(LocalTime.of(8,0)).endTime(LocalTime.of(20,0)).build());
            return null;
        });
    }
    @Test void cancellationReleasesReservationExactlyOnceWithoutReversingActualUsage() {
        var f=fixture(); long id=book(f,f.ticketId());
        tx(() -> { var t=tickets.findById(f.ticketId()).orElseThrow(); assertEquals(0,t.getUsedSessions()); assertEquals(1,t.getReservedSessions()); return null; });
        as(f.customer(), () -> { appointments.cancelAppointment(id); appointments.cancelAppointment(id); return null; });
        tx(() -> { var t=tickets.findById(f.ticketId()).orElseThrow(); assertEquals(0,t.getUsedSessions()); assertEquals(0,t.getReservedSessions());
            assertEquals(2,movements.findByTicketIdOrderByIdDesc(f.ticketId(),org.springframework.data.domain.PageRequest.of(0,20)).getTotalElements()); return null; });
    }
    @Test void itemPerformanceConsumesQuotaOnceAndVisitCannotCompleteWithoutItemOutcome() {
        var f=fixture(); long id=book(f,f.ticketId()); status(f,id,AppointmentStatus.CONFIRMED,null); readyToStart(id);
        status(f,id,AppointmentStatus.IN_PROGRESS,null);
        assertThrows(BusinessException.class, () -> status(f,id,AppointmentStatus.COMPLETED,null));
        long item=itemId(id); item(f,id,item,AppointmentItemExecutionStatus.IN_PROGRESS,null);
        item(f,id,item,AppointmentItemExecutionStatus.PERFORMED,"Completed facial");
        item(f,id,item,AppointmentItemExecutionStatus.PERFORMED,"Repeated request");
        status(f,id,AppointmentStatus.COMPLETED,null);
        tx(() -> { var t=tickets.findById(f.ticketId()).orElseThrow(); assertEquals(1,t.getUsedSessions()); assertEquals(0,t.getReservedSessions()); assertEquals(TicketStatus.COMPLETED,t.getStatus());
            var a=appointmentRows.findById(id).orElseThrow(); assertNotNull(a.getActualCompletedAt()); assertNotNull(a.getItems().getFirst().getActualStartedAt());
            assertEquals(2,movements.findByTicketIdOrderByIdDesc(f.ticketId(),org.springframework.data.domain.PageRequest.of(0,20)).getTotalElements()); return null; });
    }
    @Test void skipRequiresReasonAndReturnsHeldQuotaWithoutChargingAService() {
        var f=fixture(); long id=book(f,f.ticketId()); status(f,id,AppointmentStatus.CONFIRMED,null); readyToStart(id); status(f,id,AppointmentStatus.IN_PROGRESS,null);
        long item=itemId(id); assertThrows(BusinessException.class, () -> item(f,id,item,AppointmentItemExecutionStatus.SKIPPED,null));
        item(f,id,item,AppointmentItemExecutionStatus.SKIPPED,"Customer declined before treatment"); status(f,id,AppointmentStatus.COMPLETED,null);
        tx(() -> { var t=tickets.findById(f.ticketId()).orElseThrow(); assertEquals(0,t.getUsedSessions()); assertEquals(0,t.getReservedSessions()); return null; });
    }
    @Test void checkedInCustomerCannotBeNoShowAndRepeatedCheckInPreservesEvidence() {
        var f=fixture(); long id=book(f,null); status(f,id,AppointmentStatus.CONFIRMED,null);
        tx(() -> { appointmentRows.findById(id).orElseThrow().setAppointmentDate(LocalDate.now(SpaTimeRules.ZONE)); return null; });
        var first=as(f.admin(), () -> appointments.checkIn(id)); var again=as(f.admin(), () -> appointments.checkIn(id));
        assertEquals(first.getCheckedInAt(),again.getCheckedInAt());
        assertThrows(BusinessException.class, () -> status(f,id,AppointmentStatus.NO_SHOW,"Customer absent"));
        assertThrows(AccessDeniedException.class, () -> as(f.otherCustomer(), () -> appointments.checkIn(id)));
    }
    @Test void defaultNoShowForfeitsHeldQuotaButDoesNotRecordPerformedService() {
        var f=fixture(); long id=book(f,f.ticketId()); status(f,id,AppointmentStatus.CONFIRMED,null);
        tx(() -> { appointmentRows.findById(id).orElseThrow().setAppointmentDate(LocalDate.now(SpaTimeRules.ZONE).minusDays(1)); return null; });
        status(f,id,AppointmentStatus.NO_SHOW,"Customer did not arrive");
        tx(() -> { var i=appointmentRows.findById(id).orElseThrow().getItems().getFirst(); assertEquals(TicketUsageState.FORFEITED,i.getTicketUsageState());
            assertEquals(AppointmentItemExecutionStatus.NO_SHOW,i.getExecutionStatus()); assertNull(i.getActualStartedAt()); return null; });
    }
    @Test void pendingExpirationReleasesQuotaAndKeepsAnActionTrace() {
        var f=fixture(); long id=book(f,f.ticketId());
        tx(() -> { appointmentRows.findById(id).orElseThrow().setPendingExpiresAt(Instant.now().minusSeconds(1)); return null; });
        implementation.expirePending(id); implementation.expirePending(id);
        tx(() -> { var t=tickets.findById(f.ticketId()).orElseThrow(); assertEquals(0,t.getReservedSessions()); assertEquals(0,t.getUsedSessions());
            assertEquals(AppointmentStatus.CANCELLED,appointmentRows.findById(id).orElseThrow().getStatus());
            assertTrue(actions.findByAppointmentIdOrderByIdDesc(id,org.springframework.data.domain.PageRequest.of(0,20)).stream().anyMatch(a->a.getAction().equals("EXPIRE_PENDING"))); return null; });
    }
    @Test void serviceStartExpiryModeRejectsBookingBeyondExpiryAndAllowsEarlierBookingTimeMode() {
        var f=fixture(); tx(() -> { var t=tickets.findById(f.ticketId()).orElseThrow(); t.setExpiryDate(Instant.now().plusSeconds(3600)); t.setExpiryCheckMode("SERVICE_START"); return null; });
        assertThrows(BusinessException.class, () -> book(f,f.ticketId()));
        tx(() -> { var t=tickets.findById(f.ticketId()).orElseThrow(); assertEquals(0,t.getReservedSessions()); t.setExpiryCheckMode("BOOKING_TIME"); return null; });
        assertTrue(book(f,f.ticketId())>0);
    }
    @Test void administrativeCompensationRequiresReasonAndDeduplicatesStableRequestKey() {
        var f=fixture(); String key=UUID.randomUUID().toString();
        assertThrows(BusinessException.class, () -> as(f.admin(), () -> ticketAdmin.compensate(f.ticketId(),f.serviceId(),1,"",key)));
        as(f.admin(), () -> ticketAdmin.compensate(f.ticketId(),f.serviceId(),1,"Service recovery approved",key));
        as(f.admin(), () -> ticketAdmin.compensate(f.ticketId(),f.serviceId(),1,"Service recovery approved",key));
        assertEquals(2,tickets.findById(f.ticketId()).orElseThrow().getTotalSessions());
        assertThrows(BusinessException.class, () -> as(f.admin(), () -> ticketAdmin.compensate(f.ticketId(),f.serviceId(),2,"Service recovery approved",key)));
    }
    @Test void eachReservationLedgerRowCapturesItsOwnBalanceForAMultiServiceBooking() {
        var f=fixture(); tx(() -> { var t=tickets.findById(f.ticketId()).orElseThrow(); t.setTotalSessions(5); t.getEntitlements().get(f.serviceId()).setTotal(5); return null; });
        var request=booking(f,f.firstStaffId(),LocalTime.of(10,0),f.ticketId());
        request.setItems(List.of(request.getItems().getFirst(),request.getItems().getFirst()));
        as(f.customer(), () -> appointments.bookAppointment(request));
        var ledger=movements.findByTicketIdOrderByIdDesc(f.ticketId(),org.springframework.data.domain.PageRequest.of(0,20)).getContent();
        assertEquals(2,ledger.size()); assertEquals(2,ledger.getFirst().getReservedAfter()); assertEquals(3,ledger.getFirst().getAvailableAfter());
        assertEquals(1,ledger.getLast().getReservedAfter()); assertEquals(4,ledger.getLast().getAvailableAfter());
    }
    @Test void therapistCannotStartAnotherVisitWhileAnEarlierServiceHasOverrunItsSlot() {
        var f=fixture(); long first=book(f,null);
        long second=as(f.customer(), () -> appointments.bookAppointment(booking(f,f.firstStaffId(),LocalTime.of(12,0),null))).getId();
        status(f,first,AppointmentStatus.CONFIRMED,null); status(f,second,AppointmentStatus.CONFIRMED,null);
        readyToStart(first); readyToStart(second); status(f,first,AppointmentStatus.IN_PROGRESS,null); status(f,second,AppointmentStatus.IN_PROGRESS,null);
        item(f,first,itemId(first),AppointmentItemExecutionStatus.IN_PROGRESS,null);
        assertThrows(BusinessException.class, () -> item(f,second,itemId(second),AppointmentItemExecutionStatus.IN_PROGRESS,null));
        item(f,first,itemId(first),AppointmentItemExecutionStatus.PERFORMED,null);
        item(f,second,itemId(second),AppointmentItemExecutionStatus.IN_PROGRESS,null);
    }

    @Test void therapistListAndDetailRespectAssignmentAndDeletedStaffScope() {
        var f=fixture(); long own=book(f,null);
        long other=as(f.customer(), () -> appointments.bookAppointment(booking(f,f.secondStaffId(),LocalTime.of(12,0),null))).getId();
        var therapist=tx(() -> {
            var user=users.findById(staffs.findById(f.firstStaffId()).orElseThrow().getUserId()).orElseThrow();
            var role=roles.findByName("ROLE_SPA_THERAPIST").orElseGet(()->roles.save(com.core.beautyshop.modules.identity.domain.Role.builder().name("ROLE_SPA_THERAPIST").build()));
            user.setRoles(new java.util.ArrayList<>(List.of(role))); return users.save(user);
        });
        var page=as(therapist, () -> appointments.getAllAppointments(null,null,org.springframework.data.domain.PageRequest.of(0,20)));
        assertEquals(1,page.getTotalElements()); assertEquals(own,page.getContent().getFirst().getId());
        assertThrows(AccessDeniedException.class, () -> as(therapist, () -> appointments.getAppointmentById(other)));
        tx(() -> { staffs.findById(f.firstStaffId()).orElseThrow().setIsDeleted(true); return null; });
        assertEquals(0,as(therapist, () -> appointments.getAllAppointments(null,null,org.springframework.data.domain.PageRequest.of(0,20))).getTotalElements());
        assertThrows(AccessDeniedException.class, () -> as(therapist, () -> appointments.getAppointmentById(own)));
    }

}
