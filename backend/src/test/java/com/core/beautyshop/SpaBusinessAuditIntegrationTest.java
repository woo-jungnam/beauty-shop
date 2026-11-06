package com.core.beautyshop;

import com.core.beautyshop.modules.spa.application.service.AdminSpaTicketService;
import com.core.beautyshop.modules.spa.application.dto.request.AppointmentItemRequest;
import com.core.beautyshop.modules.spa.application.dto.request.BookAppointmentRequest;
import com.core.beautyshop.modules.spa.domain.BeautyService;
import com.core.beautyshop.modules.spa.domain.StaffServiceSkill;
import com.core.beautyshop.modules.spa.domain.treatment.TreatmentEntities.Course;
import com.core.beautyshop.modules.spa.domain.treatment.TreatmentEntities.Session;
import com.core.beautyshop.modules.spa.domain.treatment.TreatmentEntities.SessionItem;
import com.core.beautyshop.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static com.core.beautyshop.modules.spa.application.service.TreatmentContracts.*;
import static org.junit.jupiter.api.Assertions.*;

/** Regression coverage for business audit findings. */
class SpaBusinessAuditIntegrationTest extends TreatmentCourseIntegrationTest {
    @Autowired AdminSpaTicketService administrativeTickets;
    @Autowired com.core.beautyshop.modules.spa.application.service.SpaPackageRequestService packageRequests;


    @Test void auditDiscountedTreatmentRefundIsQuotedAboveActualReceipts() {
        CourseFixture f = courseFixture();
        tx(() -> {
            var customer = users.findById(f.base().customer().getId()).orElseThrow();
            customer.setMembershipTier(com.core.beautyshop.modules.identity.domain.enums.MembershipTier.GOLD);
            customer.setLoyaltyPoints(5500); return null;
        });
        var order = as(f.base().customer(), () -> treatments.purchase(new PurchaseCommand(
                f.template().id(), f.template().versionId(), true, UUID.randomUUID().toString(), null)));
        assertEquals(0, new BigDecimal("900").compareTo(order.getTotalAmount()));
        pay(f.base(), order.getOrderId());
        CourseView course = as(f.base().customer(), treatments::myCourses).stream()
                .filter(c -> c.orderId().equals(order.getOrderId())).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("900").compareTo(course.paidAmount()));
        var requested = as(f.base().customer(), () -> treatments.request(course.id(), new RequestCommand("CANCEL", "Cancel unused purchased treatment", null)));
        Long request = requested.requests().getFirst().id();
        var approved = as(f.base().admin(), () -> treatments.reviewRequest(course.id(), request, new DecisionCommand(1, "Approved cancellation"), true));
        var amount = approved.requests().getFirst().refundAmount();
        assertEquals(0, amount.compareTo(order.getTotalAmount()));
        assertDoesNotThrow(() -> as(f.base().admin(), () -> treatments.confirmRefund(course.id(), request,
                new RefundCommand(amount, "AUDIT-" + UUID.randomUUID(), "Confirmed refund evidence"))));
    }

    @Test void auditImpossibleSessionIntervalCanBePublishedAndPurchased() {
        CourseFixture f = courseFixture();
        Plan impossible = new Plan("Impossible timing", "Two required sessions", f.template().serviceId(), new BigDecimal("1000"), 1, "Accepted terms",
                List.of(new PlanSession(1, "First", 0, null, true, List.of(new PlanItem(f.base().serviceId(), null, true))),
                        new PlanSession(2, "Second", 30, 40, true, List.of(new PlanItem(f.base().serviceId(), null, true)))));
        assertThrows(BusinessException.class, () -> as(f.base().admin(), () -> treatments.saveTemplate(null, impossible)));
    }

    @Test void auditGenericCompensationAddsTreatmentQuotaWithoutCreatingAnySession() {
        CourseFixture f = courseFixture();
        int before = tx(() -> tickets.findById(f.course().ticketId()).orElseThrow().getTotalSessions());
        assertThrows(BusinessException.class, () -> as(f.base().admin(), () -> administrativeTickets.compensate(f.course().ticketId(), f.base().serviceId(), 1,
                "Additional treatment visit agreed", "audit-" + UUID.randomUUID())));
        int after = tx(() -> tickets.findById(f.course().ticketId()).orElseThrow().getTotalSessions());
        assertEquals(before, after);
        assertEquals(f.course().sessions().size(), as(f.base().customer(), () -> treatments.course(f.course().id())).sessions().size());
    }

    @Test void auditPlanValidityChangeOverwritesPreviouslyApprovedExtension() {
        CourseFixture f = courseFixture();
        var requested = as(f.base().customer(), () -> treatments.request(f.course().id(), new RequestCommand("EXTEND", "Approved long extension", 90)));
        as(f.base().admin(), () -> treatments.reviewRequest(f.course().id(), requested.requests().getFirst().id(), new DecisionCommand(1, "Extend"), true));
        Instant extended = as(f.base().customer(), () -> treatments.course(f.course().id())).expiryDate();
        Plan old = f.course().plan();
        Plan revised = new Plan(old.name(), old.description(), old.serviceId(), old.price(), 31, old.terms(), old.sessions());
        var proposed = as(f.base().admin(), () -> treatments.proposeChange(f.course().id(), new ChangeCommand(1, "Change plan validity", revised)));
        Long change = proposed.changes().getFirst().id();
        as(f.base().admin(), () -> treatments.reviewChange(f.course().id(), change, new DecisionCommand(1, "Approved"), true));
        var applied = as(f.base().customer(), () -> treatments.customerChange(f.course().id(), change, new DecisionCommand(1, "Accepted"), true));
        assertEquals(extended.plusSeconds(86400L), applied.expiryDate());
    }

    @Test void auditMultiServiceSessionCanBeBookedWithLastItemOutsideItsMaximumGap() {
        CourseFixture f = courseFixture();
        Long secondService = tx(() -> {
            var service = services.save(BeautyService.builder().name("Second component").slug(UUID.randomUUID().toString())
                    .basePrice(BigDecimal.valueOf(100)).durationMinutes(30).preparationTimeMinutes(10).build());
            skills.save(StaffServiceSkill.builder().staff(staffs.getReferenceById(f.base().firstStaffId())).service(service).isCertified(true).build());
            return service.getId();
        });
        Plan combined = new Plan("Window test", "Second visit has two items", f.template().serviceId(), BigDecimal.valueOf(1000), 30, "Accepted terms",
                List.of(new PlanSession(1, "First", 0, null, true, List.of(new PlanItem(f.base().serviceId(), null, true))),
                        new PlanSession(2, "Second", 0, 3, true, List.of(new PlanItem(f.base().serviceId(), null, true), new PlanItem(secondService, null, true)))));
        var draft = as(f.base().admin(), () -> treatments.saveTemplate(null, combined));
        var published = as(f.base().admin(), () -> treatments.publish(draft.id()));
        var purchase = as(f.base().customer(), () -> treatments.purchase(new PurchaseCommand(published.id(), published.versionId(), true, UUID.randomUUID().toString(), null)));
        pay(f.base(), purchase.getOrderId());
        var course = as(f.base().customer(), treatments::myCourses).stream().filter(c -> c.orderId().equals(purchase.getOrderId())).findFirst().orElseThrow();
        Instant latest = f.base().date().atTime(10, 30).atZone(com.core.beautyshop.modules.spa.application.service.SpaTimeRules.ZONE).toInstant();
        tx(() -> {
            var stored = entityManager.find(Course.class, course.id());
            stored.setStatus("ACTIVE"); stored.setIntakeNotes("Historical intake accepted");
            stored.setAssignedStaffId(f.base().firstStaffId());
            // Model a legitimately paid and partly performed historical course.
            Instant paidAt = latest.minusSeconds(4 * 86400L);
            orders.findById(course.orderId()).orElseThrow().setPaidAt(paidAt);
            var ticket = tickets.findById(course.ticketId()).orElseThrow();
            ticket.setExpiryDate(paidAt.plusSeconds(30 * 86400L)); ticket.setUsedSessions(1);
            ticket.getEntitlements().get(f.base().serviceId()).setUsed(1);
            stored.setExpiryDate(ticket.getExpiryDate());
            var previous = entityManager.find(Session.class, course.sessions().getFirst().id());
            previous.setStatus("COMPLETED"); previous.setResult("Historical first visit result"); previous.setCompletedAt(latest.minusSeconds(3 * 86400L));
            var item = entityManager.find(SessionItem.class, course.sessions().getFirst().items().getFirst().id());
            item.setStatus("COMPLETED"); item.setCompletedAt(previous.getCompletedAt());
            stored.setConsumedAmount(item.getAllocatedAmount()); return null;
        });
        var second = course.sessions().get(1);
        assertFalse(as(f.base().customer(), () -> treatments.availableSlots(course.id(), second.id(), f.base().date(), f.base().firstStaffId())).contains("10:00"));
        var command = new BookAppointmentRequest(); command.setAppointmentDate(f.base().date()); command.setStartTime(LocalTime.of(10, 0));
        command.setItems(second.items().stream().map(i -> {
            var item = new AppointmentItemRequest(); item.setServiceId(i.serviceId()); item.setStaffId(f.base().firstStaffId());
            item.setTicketId(course.ticketId()); item.setTreatmentSessionItemId(i.id()); return item;
        }).toList());
        assertThrows(BusinessException.class, () -> as(f.base().customer(), () -> appointments.bookAppointment(command)));
    }
    @Test void pausedTreatmentPreservesElapsedTimeOnResumeEvenAfterOriginalExpiry() {
        CourseFixture f=courseFixture();
        var requested=as(f.base().customer(), () -> treatments.request(f.course().id(),new RequestCommand("PAUSE","Pause approved for travel",null)));
        as(f.base().admin(), () -> treatments.reviewRequest(f.course().id(),requested.requests().getFirst().id(),new DecisionCommand(1,"Approved pause"),true));
        Instant original=Instant.now().minusSeconds(86400);
        tx(() -> { var c=entityManager.find(Course.class,f.course().id()); c.setPausedAt(original.minusSeconds(86400)); c.setExpiryDate(original); return null; });
        var resume=as(f.base().customer(), () -> treatments.request(f.course().id(),new RequestCommand("RESUME","Return from travel",null)));
        var resumed=as(f.base().admin(), () -> treatments.reviewRequest(f.course().id(),resume.requests().getFirst().id(),new DecisionCommand(1,"Approved resume"),true));
        assertEquals("PENDING_INTAKE",resumed.status());
        assertTrue(resumed.expiryDate().isAfter(Instant.now().plusSeconds(86000)));
        assertTrue(java.time.Duration.between(resumed.expiryDate(), tx(() -> tickets.findById(f.course().ticketId()).orElseThrow().getExpiryDate())).abs().compareTo(java.time.Duration.ofNanos(1000)) <= 0);
    }

    @Test void packageOwnerCanRequestExtensionButCannotApproveOrReadAnotherOwnersRequests() {
        var f=fixture();
        var command=new com.core.beautyshop.modules.spa.application.service.SpaPackageRequestService.Command("EXTEND","Need more time to use package",7);
        var request=as(f.customer(), () -> packageRequests.create(f.ticketId(),command));
        assertThrows(BusinessException.class, () -> as(f.customer(), () -> packageRequests.create(f.ticketId(),command)));
        assertThrows(BusinessException.class, () -> as(f.otherCustomer(), () -> packageRequests.list(f.ticketId())));
        var decision=new com.core.beautyshop.modules.spa.application.service.SpaPackageRequestService.Decision(true,"Approved seven extra days");
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> as(f.customer(), () -> packageRequests.review(f.ticketId(),request.getId(),decision)));
        Instant before=tx(() -> tickets.findById(f.ticketId()).orElseThrow().getExpiryDate());
        as(f.admin(), () -> packageRequests.review(f.ticketId(),request.getId(),decision));
        assertEquals(before.plusSeconds(7*86400L),tx(() -> tickets.findById(f.ticketId()).orElseThrow().getExpiryDate()));
        assertThrows(BusinessException.class, () -> as(f.admin(), () -> packageRequests.review(f.ticketId(),request.getId(),decision)));
    }

    @Test void discountedCoursePriceReductionRefundsOnlyNetDifferenceBeforeCancellation() {
        CourseFixture f=courseFixture();
        tx(() -> { var user=users.findById(f.base().customer().getId()).orElseThrow(); user.setMembershipTier(com.core.beautyshop.modules.identity.domain.enums.MembershipTier.GOLD); user.setLoyaltyPoints(5500); return null; });
        var order=as(f.base().customer(), () -> treatments.purchase(new PurchaseCommand(f.template().id(),f.template().versionId(),true,UUID.randomUUID().toString(),null)));
        pay(f.base(),order.getOrderId());
        var c=as(f.base().customer(),treatments::myCourses).stream().filter(x -> x.orderId().equals(order.getOrderId())).findFirst().orElseThrow();
        Plan old=c.plan(); Plan reduced=new Plan(old.name(),old.description(),old.serviceId(),BigDecimal.valueOf(800),old.validityDays(),old.terms(),old.sessions());
        var proposal=as(f.base().admin(), () -> treatments.proposeChange(c.id(),new ChangeCommand(1,"Reduce contracted package price",reduced)));
        Long change=proposal.changes().getFirst().id();
        as(f.base().admin(), () -> treatments.reviewChange(c.id(),change,new DecisionCommand(1,"Approved reduction"),true));
        var applied=as(f.base().customer(), () -> treatments.customerChange(c.id(),change,new DecisionCommand(1,"Accepted reduction"),true));
        assertEquals(0,BigDecimal.valueOf(720).compareTo(applied.sessions().stream().flatMap(x -> x.items().stream()).map(ItemView::allocatedAmount).reduce(BigDecimal.ZERO,BigDecimal::add)));
        var refund=applied.requests().getFirst(); assertEquals(0,BigDecimal.valueOf(180).compareTo(refund.refundAmount()));
        as(f.base().admin(), () -> treatments.confirmRefund(c.id(),refund.id(),new RefundCommand(refund.refundAmount(),"REDUCE-"+UUID.randomUUID(),"Evidence of reduction refund")));
        var cancellation=as(f.base().customer(), () -> treatments.request(c.id(),new RequestCommand("CANCEL","Cancel remaining unused treatment",null)));
        var cancelled=as(f.base().admin(), () -> treatments.reviewRequest(c.id(),cancellation.requests().getFirst().id(),new DecisionCommand(2,"Approved final cancellation"),true));
        var finalRefund=cancelled.requests().getFirst(); assertEquals(0,BigDecimal.valueOf(720).compareTo(finalRefund.refundAmount()));
        assertDoesNotThrow(() -> as(f.base().admin(), () -> treatments.confirmRefund(c.id(),finalRefund.id(),new RefundCommand(finalRefund.refundAmount(),"CANCEL-"+UUID.randomUUID(),"Evidence of remaining refund"))));
    }

    @Test void adminTicketFilterUsesEffectiveExpiryWithoutWaitingForBackgroundJobs() {
        var f=fixture();
        tx(() -> { tickets.findById(f.ticketId()).orElseThrow().setExpiryDate(Instant.now().minusSeconds(1)); return null; });
        var expired=as(f.admin(), () -> administrativeTickets.find(f.customer().getId(),com.core.beautyshop.modules.spa.domain.enums.TicketStatus.EXPIRED,org.springframework.data.domain.PageRequest.of(0,20)));
        assertTrue(expired.stream().anyMatch(t -> t.getId().equals(f.ticketId()) && t.getStatus()==com.core.beautyshop.modules.spa.domain.enums.TicketStatus.EXPIRED));
        var active=as(f.admin(), () -> administrativeTickets.find(f.customer().getId(),com.core.beautyshop.modules.spa.domain.enums.TicketStatus.ACTIVE,org.springframework.data.domain.PageRequest.of(0,20)));
        assertTrue(active.stream().noneMatch(t -> t.getId().equals(f.ticketId())));
    }

    @Test void extensionDuringPauseDoesNotDoubleCountThePausedInterval() {
        CourseFixture f=courseFixture();
        Instant paused=Instant.now().minusSeconds(10*86400L).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        Instant original=paused.plusSeconds(86400L);
        tx(() -> { var c=entityManager.find(Course.class,f.course().id()); c.setStatus("PAUSED"); c.setPausedAt(paused); c.setExpiryDate(original); return null; });
        var request=as(f.base().customer(), () -> treatments.request(f.course().id(),new RequestCommand("EXTEND","Extend preserved validity",7)));
        var extended=as(f.base().admin(), () -> treatments.reviewRequest(f.course().id(),request.requests().getFirst().id(),new DecisionCommand(1,"Approved extension"),true));
        assertEquals(original.plusSeconds(7*86400L),extended.expiryDate());
        assertEquals("PAUSED",extended.status());
        Plan old=f.course().plan(); Plan revised=new Plan(old.name(),old.description(),old.serviceId(),old.price(),31,old.terms(),old.sessions());
        var proposal=as(f.base().admin(), () -> treatments.proposeChange(f.course().id(),new ChangeCommand(1,"Revise validity during preserved pause",revised)));
        Long change=proposal.changes().getFirst().id();
        as(f.base().admin(), () -> treatments.reviewChange(f.course().id(),change,new DecisionCommand(1,"Approved paused revision"),true));
        var applied=as(f.base().customer(), () -> treatments.customerChange(f.course().id(),change,new DecisionCommand(1,"Accepted paused revision"),true));
        assertEquals(original.plusSeconds(8*86400L),applied.expiryDate());

    }

    @Test void pendingIncidentMustBeReviewedBeforeResumingTreatment() {
        CourseFixture f=courseFixture();
        as(f.base().customer(), () -> treatments.request(f.course().id(),new RequestCommand("INCIDENT","Reaction reported before next treatment",null)));
        var resume=as(f.base().customer(), () -> treatments.request(f.course().id(),new RequestCommand("RESUME","Request to resume treatment",null)));
        assertThrows(BusinessException.class, () -> as(f.base().admin(), () -> treatments.reviewRequest(f.course().id(),resume.requests().getFirst().id(),new DecisionCommand(1,"Review resume request"),true)));
        assertEquals("PENDING_REVIEW",as(f.base().customer(), () -> treatments.course(f.course().id())).status());
    }

}
