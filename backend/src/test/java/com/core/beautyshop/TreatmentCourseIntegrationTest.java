package com.core.beautyshop;

import com.core.beautyshop.modules.identity.domain.User;
import com.core.beautyshop.modules.order.domain.enums.*;
import com.core.beautyshop.modules.spa.api.event.SpaPackagePaidEvent;
import com.core.beautyshop.modules.spa.application.dto.request.*;
import com.core.beautyshop.modules.spa.application.service.*;
import com.core.beautyshop.modules.spa.domain.*;
import com.core.beautyshop.modules.spa.domain.enums.*;
import com.core.beautyshop.modules.spa.domain.treatment.TreatmentEntities.*;
import com.core.beautyshop.shared.exception.BusinessException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.core.beautyshop.modules.spa.application.service.TreatmentContracts.*;
import static org.junit.jupiter.api.Assertions.*;

/** The MySQL subclass uses Flyway, actual foreign keys and production lock isolation. */
class TreatmentCourseIntegrationTest extends SpaWorkflowTestSupport {
    @Autowired TreatmentCourseService treatments;
    @Autowired TreatmentAppointmentBridge treatmentAppointments;
    @Autowired PaidSpaTicketIssuer issuer;
    @Autowired SpaPurchaseSnapshotRepository snapshots;
    @Autowired com.core.beautyshop.modules.order.domain.RefundConfirmationRepository refundRows;
    @PersistenceContext EntityManager entityManager;

    record CourseFixture(Fixture base, TemplateView template, CourseView course, User responsible, User unrelated) {}

    Plan plan(long parent, long service, BigDecimal price, int sessions) {
        List<PlanSession> result = new ArrayList<>();
        for (int i = 1; i <= sessions; i++) result.add(new PlanSession(i, "Visit " + i, 0, null, true, List.of(new PlanItem(service, null, true))));
        return new Plan("Treatment fixture", "A published plan", parent, price, 30, "Published terms accepted by the customer", result);
    }

    CourseFixture courseFixture() {
        Fixture f = fixture();
        long parent = tx(() -> services.saveAndFlush(BeautyService.builder().name("Treatment parent").slug(UUID.randomUUID().toString())
                .serviceType("TREATMENT_COURSE").basePrice(BigDecimal.valueOf(1000)).durationMinutes(30).build()).getId());
        TemplateView draft = as(f.admin(), () -> treatments.saveTemplate(null, plan(parent, f.serviceId(), BigDecimal.valueOf(1000), 2)));
        TemplateView published = as(f.admin(), () -> treatments.publish(draft.id()));
        var order = as(f.customer(), () -> treatments.purchase(new PurchaseCommand(published.id(), published.versionId(), true, UUID.randomUUID().toString(), null)));
        pay(f, order.getOrderId());
        CourseView course = as(f.customer(), () -> treatments.myCourses()).stream().filter(c -> c.templateId().equals(published.id())).findFirst().orElseThrow();
        User responsible = tx(() -> users.findById(staffs.findById(f.firstStaffId()).orElseThrow().getUserId()).orElseThrow());
        User unrelated = tx(() -> users.findById(staffs.findById(f.secondStaffId()).orElseThrow().getUserId()).orElseThrow());
        return new CourseFixture(f, published, course, responsible, unrelated);
    }

    void pay(Fixture f, Long orderId) {
        Long packageId = tx(() -> {
            var order = orders.findById(orderId).orElseThrow(); order.setPaidAt(Instant.now()); order.setPaidAmount(order.getTotalAmount());
            order.setPaymentStatus(PaymentStatus.PAID); order.setStatus(OrderStatus.PROCESSING); order.setPaymentDeadline(null); return order.getServicePackageId();
        });
        issuer.issue(new SpaPackagePaidEvent(orderId, f.customer().getId(), packageId));
    }

    @Test void purchaseSnapshotUsesActualDiscountedOrderTotal() {
        CourseFixture f = courseFixture();
        tx(() -> { var customer=users.findById(f.base().customer().getId()).orElseThrow();
            customer.setMembershipTier(com.core.beautyshop.modules.identity.domain.enums.MembershipTier.GOLD);
            customer.setLoyaltyPoints(5500); return null; });
        var result=as(f.base().customer(),()->treatments.purchase(new PurchaseCommand(f.template().id(),f.template().versionId(),true,UUID.randomUUID().toString(),null)));
        assertEquals(0,new BigDecimal("900").compareTo(result.getTotalAmount()));
        tx(() -> { var snapshot=snapshots.findById(result.getOrderId()).orElseThrow();
            assertEquals(0,result.getTotalAmount().compareTo(snapshot.getPurchaseAmount())); return null; });
    }

    CourseView assignAndIntake(CourseFixture f) {
        as(f.base().admin(), () -> treatments.assign(f.course().id(), new AssignCommand(f.base().firstStaffId(), "Responsible therapist")));
        return as(f.responsible(), () -> treatments.intake(f.course().id(), new IntakeCommand("Private intake assessment")));
    }

    BookAppointmentRequest courseBooking(CourseFixture f, SessionView session, LocalTime start) {
        BookAppointmentRequest command = booking(f.base(), f.base().firstStaffId(), start, f.course().ticketId());
        command.getItems().getFirst().setTreatmentSessionItemId(session.items().getFirst().id()); return command;
    }

    long bookCourse(CourseFixture f) {
        return as(f.base().customer(), () -> appointments.bookAppointment(courseBooking(f, f.course().sessions().getFirst(), LocalTime.of(10, 0)))).getId();
    }

    User withPermissions(User user, String... permissions) {
        return tx(() -> {
            User stored = users.findById(user.getId()).orElseThrow();
            stored.setStaffPermissions(new HashSet<>(List.of(permissions)));
            return users.saveAndFlush(stored);
        });
    }

    @Test void purchaseIsIdempotentSnapshotIsImmutableAndPaidCallbacksDoNotDuplicateEnrollment() {
        CourseFixture f = courseFixture();
        String key = UUID.randomUUID().toString();
        var request = new PurchaseCommand(f.template().id(), f.template().versionId(), true, key, "First purchase");
        var first = as(f.base().customer(), () -> treatments.purchase(request));
        var retry = as(f.base().customer(), () -> treatments.purchase(request)); assertEquals(first.getOrderId(), retry.getOrderId());
        assertEquals(1, as(f.base().customer(), () -> treatments.myCourses()).stream().filter(c -> c.templateId().equals(f.template().id())).count());
        assertThrows(BusinessException.class, () -> as(f.base().customer(), () -> treatments.purchase(new PurchaseCommand(f.template().id(), f.template().versionId(), true, key, "Different request"))));
        pay(f.base(), first.getOrderId()); pay(f.base(), first.getOrderId());
        assertEquals(2, as(f.base().customer(), () -> treatments.myCourses()).stream().filter(c -> c.templateId().equals(f.template().id())).count());
        as(f.base().admin(), () -> treatments.newVersion(f.template().id(), plan(f.template().serviceId(), f.base().serviceId(), BigDecimal.valueOf(2000), 3)));
        as(f.base().admin(), () -> treatments.publish(f.template().id()));
        CourseView original = as(f.base().customer(), () -> treatments.course(f.course().id()));
        assertEquals(2, original.sessions().size()); assertEquals(0, BigDecimal.valueOf(1000).compareTo(original.plan().price()));
        assertEquals(f.template().versionId(), original.versionId());
    }

    @Test void clinicalWritesAndPrivateNotesRequireAssignedStaffAndCustomersCannotConfirmForOthers() {
        CourseFixture f = courseFixture(); assignAndIntake(f);
        withPermissions(f.unrelated(), "SPA_EXECUTION");
        assertThrows(AccessDeniedException.class, () -> as(f.unrelated(), () -> treatments.course(f.course().id())));
        assertThrows(AccessDeniedException.class, () -> as(f.unrelated(), () -> treatments.intake(f.course().id(), new IntakeCommand("Not assigned"))));
        assertThrows(AccessDeniedException.class, () -> as(f.base().otherCustomer(), () -> treatments.course(f.course().id())));
        assertNull(as(f.base().customer(), () -> treatments.course(f.course().id())).intakeNotes());
        assertEquals("Private intake assessment", as(f.responsible(), () -> treatments.course(f.course().id())).intakeNotes());
        assertThrows(AccessDeniedException.class, () -> as(f.base().customer(), () -> treatments.saveTemplate(null, f.course().plan())));
    }

    @Test void executionGrantListsOnlyAssignedCoursesAndCannotClaimAnUnassignedCustomer() {
        CourseFixture f = courseFixture();
        withPermissions(f.responsible(), "SPA_EXECUTION");
        withPermissions(f.unrelated(), "SPA_EXECUTION");
        assertFalse(as(f.responsible(), treatments::operationalCourses).stream().anyMatch(c -> c.id().equals(f.course().id())));
        assertThrows(AccessDeniedException.class, () -> as(f.responsible(), () -> treatments.course(f.course().id())));
        assertThrows(AccessDeniedException.class, () -> as(f.responsible(), () -> treatments.assign(f.course().id(), new AssignCommand(f.base().firstStaffId(), "Self assignment"))));
        assignAndIntake(f);
        assertTrue(as(f.responsible(), treatments::operationalCourses).stream().anyMatch(c -> c.id().equals(f.course().id())));
        assertFalse(as(f.unrelated(), treatments::operationalCourses).stream().anyMatch(c -> c.id().equals(f.course().id())));
        assertThrows(AccessDeniedException.class, () -> as(f.unrelated(), () -> treatments.request(f.course().id(), new RequestCommand("INCIDENT", "Not responsible for this customer", null))));
        assertThrows(AccessDeniedException.class, () -> as(f.responsible(), () -> treatments.assign(f.course().id(), new AssignCommand(f.base().secondStaffId(), "Reassign own course"))));
    }

    @Test void receptionCanCoordinateAndReassignAnAssignedCourseWithoutClinicalAccess() {
        CourseFixture f = courseFixture();
        User reception = withPermissions(f.unrelated(), "SPA_RECEPTION");
        as(reception, () -> treatments.assign(f.course().id(), new AssignCommand(f.base().firstStaffId(), "Schedule responsible therapist")));
        as(f.responsible(), () -> treatments.intake(f.course().id(), new IntakeCommand("Private intake assessment")));
        CourseView operational = as(reception, () -> treatments.course(f.course().id()));
        assertEquals(f.base().firstStaffId(), operational.assignedStaffId());
        assertNull(operational.intakeNotes()); assertNull(operational.summary());
        assertTrue(operational.audit().isEmpty()); assertTrue(operational.requests().isEmpty()); assertTrue(operational.changes().isEmpty());
        assertTrue(operational.allowedActions().contains("ASSIGN"));
        assertFalse(operational.allowedActions().contains("INTAKE"));
        assertFalse(operational.allowedActions().contains("PROPOSE_CHANGE"));
        assertTrue(as(reception, treatments::operationalCourses).stream().anyMatch(c -> c.id().equals(f.course().id()) && c.intakeNotes() == null));
        assertThrows(AccessDeniedException.class, () -> as(reception, () -> treatments.intake(f.course().id(), new IntakeCommand("Reception cannot assess"))));
        assertThrows(AccessDeniedException.class, () -> as(reception, () -> treatments.proposeChange(f.course().id(), new ChangeCommand(1, "Clinical revision", f.course().plan()))));
        CourseView reassigned = as(reception, () -> treatments.assign(f.course().id(), new AssignCommand(f.base().secondStaffId(), "Coordinate replacement")));
        assertEquals(f.base().secondStaffId(), reassigned.assignedStaffId());
        assertNull(reassigned.intakeNotes());
        assertThrows(AccessDeniedException.class, () -> as(reception, () -> treatments.intake(f.course().id(), new IntakeCommand("Assignment does not grant execution access"))));
    }

    @Test void firstBookingBeforeIntakeIsAllowedButExecutionAndOutOfOrderBookingsAreBlocked() {
        CourseFixture f = courseFixture(); long appointment = bookCourse(f);
        assertThrows(BusinessException.class, () -> as(f.base().admin(), () -> tx(() -> { treatmentAppointments.validateAppointmentStart(appointmentRows.findById(appointment).orElseThrow()); return null; })));
        assertThrows(BusinessException.class, () -> as(f.base().customer(), () -> appointments.bookAppointment(courseBooking(f, f.course().sessions().getLast(), LocalTime.of(12, 0)))));
        assertThrows(BusinessException.class, () -> as(f.base().customer(), () -> appointments.bookAppointment(booking(f.base(), f.base().secondStaffId(), LocalTime.of(14, 0), f.course().ticketId()))));
        var parentBooking = booking(f.base(), f.base().firstStaffId(), LocalTime.of(16, 0), null); parentBooking.getItems().getFirst().setServiceId(f.template().serviceId());
        assertThrows(BusinessException.class, () -> as(f.base().customer(), () -> appointments.bookAppointment(parentBooking)));
    }

    @Test void canceledAttemptCanBeRebookedAndPlanRevisionPreservesItsHistoricalForeignKey() {
        CourseFixture f = courseFixture(); assignAndIntake(f); long appointment = bookCourse(f);
        as(f.base().customer(), () -> { appointments.cancelAppointment(appointment); return null; });
        assertEquals("PLANNED", as(f.base().customer(), () -> treatments.course(f.course().id())).sessions().getFirst().status());
        Plan changed = plan(f.template().serviceId(), f.base().serviceId(), BigDecimal.valueOf(1000), 3);
        CourseView proposed = as(f.responsible(), () -> treatments.proposeChange(f.course().id(), new ChangeCommand(1, "Three shorter visits", changed)));
        Long change = proposed.changes().getFirst().id();
        as(f.base().admin(), () -> treatments.reviewChange(f.course().id(), change, new DecisionCommand(1, "Approved"), true));
        assertThrows(AccessDeniedException.class, () -> as(f.base().admin(), () -> treatments.customerChange(f.course().id(), change, new DecisionCommand(1, null), true)));
        CourseView applied = as(f.base().customer(), () -> treatments.customerChange(f.course().id(), change, new DecisionCommand(1, null), true));
        assertEquals(2, applied.version()); assertEquals(3, applied.sessions().size());
        assertEquals(applied.version(), as(f.base().customer(), () -> treatments.customerChange(f.course().id(), change, new DecisionCommand(1, null), true)).version());
        assertThrows(BusinessException.class, () -> as(f.base().customer(), () -> treatments.customerChange(f.course().id(), change, new DecisionCommand(2, null), true)));
        tx(() -> {
            AppointmentItem old = appointmentRows.findById(appointment).orElseThrow().getItems().getFirst();
            assertNotNull(entityManager.find(SessionItem.class, old.getTreatmentSessionItemId()));
            assertTrue(entityManager.find(SessionItem.class, old.getTreatmentSessionItemId()).isRetired()); return null;
        });
        assertNotEquals(f.course().sessions().getFirst().id(), applied.sessions().getFirst().id());
    }

    @Test void pauseReleasesFutureReservationsAndOldExtensionCannotReopenCanceledTreatment() {
        CourseFixture f = courseFixture(); assignAndIntake(f); long appointment = bookCourse(f);
        CourseView requested = as(f.base().customer(), () -> treatments.request(f.course().id(), new RequestCommand("PAUSE", "Unavailable next month", null)));
        assertEquals("ACTIVE", requested.status());
        CourseView paused = as(f.base().admin(), () -> treatments.reviewRequest(f.course().id(), requested.requests().getFirst().id(), new DecisionCommand(1, "Approved pause"), true));
        assertEquals("PAUSED", paused.status()); assertEquals(AppointmentStatus.CANCELLED, appointmentRows.findById(appointment).orElseThrow().getStatus());
        assertEquals(0, tickets.findById(f.course().ticketId()).orElseThrow().getReservedSessions());
        CourseView extension = as(f.base().customer(), () -> treatments.request(f.course().id(), new RequestCommand("EXTEND", "Additional validity", 10)));
        Long extensionId = extension.requests().getFirst().id();
        CourseView cancel = as(f.base().customer(), () -> treatments.request(f.course().id(), new RequestCommand("CANCEL", "Stop treatment", null)));
        as(f.base().admin(), () -> treatments.reviewRequest(f.course().id(), cancel.requests().getFirst().id(), new DecisionCommand(1, "Approved cancellation"), true));
        assertThrows(BusinessException.class, () -> as(f.base().admin(), () -> treatments.reviewRequest(f.course().id(), extensionId, new DecisionCommand(1, "Cannot reopen"), true)));
    }

    @Test void resultsAreAppendOnlyProgressRequiresActualPerformanceAndPrivateNotesNeverReachCustomer() {
        CourseFixture f = courseFixture(); assignAndIntake(f);
        Long sessionId = f.course().sessions().getFirst().id();
        assertThrows(BusinessException.class, () -> as(f.responsible(), () -> treatments.sessionResult(f.course().id(), sessionId, new ResultCommand("Invented result", null, null, true))));
        long appointment = bookCourse(f);
        // The bridge consumes real appointment execution evidence. Ticket redemption is independently covered by execution integration tests.
        as(f.base().admin(), () -> tx(() -> {
            AppointmentItem item = appointmentRows.findById(appointment).orElseThrow().getItems().getFirst(); item.setExecutionStatus(AppointmentItemExecutionStatus.PERFORMED);
            item.setActualStartedAt(Instant.now().minusSeconds(60)); item.setActualCompletedAt(Instant.now()); treatmentAppointments.synchronizeExecution(item);
            item.getAppointment().setStatus(AppointmentStatus.COMPLETED); return null;
        }));
        as(f.responsible(), () -> treatments.sessionResult(f.course().id(), sessionId, new ResultCommand("First result", "First aftercare", "Internal finding one", true)));
        CourseView latest = as(f.responsible(), () -> treatments.sessionResult(f.course().id(), sessionId, new ResultCommand("Corrected result", "Updated aftercare", "Internal finding two", true)));
        assertEquals(1, latest.completedSessions()); assertEquals(2, latest.sessions().getFirst().results().size());
        CourseView customer = as(f.base().customer(), () -> treatments.course(f.course().id()));
        assertNull(customer.sessions().getFirst().internalNotes()); assertTrue(customer.audit().isEmpty());
        assertTrue(customer.sessions().getFirst().results().stream().allMatch(r -> r.internalNotes() == null));
        User reception = withPermissions(f.unrelated(), "SPA_RECEPTION");
        CourseView coordinated = as(reception, () -> treatments.course(f.course().id()));
        assertNull(coordinated.intakeNotes()); assertNull(coordinated.summary()); assertTrue(coordinated.audit().isEmpty());
        assertNull(coordinated.sessions().getFirst().result()); assertNull(coordinated.sessions().getFirst().aftercare());
        assertNull(coordinated.sessions().getFirst().internalNotes()); assertTrue(coordinated.sessions().getFirst().results().isEmpty());
        assertTrue(coordinated.sessions().getFirst().items().stream().allMatch(i -> i.result() == null && i.aftercare() == null));
        assertThrows(BusinessException.class, () -> as(f.responsible(), () -> treatments.complete(f.course().id(), new CompleteCommand("Too early"))));
    }

    @Test void cancellationRefundRequiresActualConfirmationAndSharedLedgerDeduplicatesReference() {
        CourseFixture f = courseFixture(); assignAndIntake(f);
        CourseView request = as(f.base().customer(), () -> treatments.request(f.course().id(), new RequestCommand("CANCEL", "Cannot continue", null)));
        Long requestId = request.requests().getFirst().id();
        CourseView approved = as(f.base().admin(), () -> treatments.reviewRequest(f.course().id(), requestId, new DecisionCommand(1, "Approve unused rights refund"), true));
        assertEquals("AWAITING_REFUND", approved.requests().getFirst().status());
        assertEquals(0, orders.findById(f.course().orderId()).orElseThrow().getRefundedAmount().signum());
        String reference = "REF-" + UUID.randomUUID();
        RefundCommand confirmation = new RefundCommand(BigDecimal.valueOf(1000), reference, "Transfer completed");
        as(f.base().admin(), () -> treatments.confirmRefund(f.course().id(), requestId, confirmation));
        as(f.base().admin(), () -> treatments.confirmRefund(f.course().id(), requestId, confirmation));
        assertEquals(0, BigDecimal.valueOf(1000).compareTo(orders.findById(f.course().orderId()).orElseThrow().getRefundedAmount()));
        assertTrue(refundRows.findByReference(reference).isPresent());
        assertEquals(TicketStatus.REVOKED, tickets.findById(f.course().ticketId()).orElseThrow().getStatus());
    }

    @Test void paidAdjustmentIssuesNoIndependentUsableRightsAndAppliesOnlyAcceptedRevision() {
        CourseFixture f = courseFixture(); assignAndIntake(f);
        CourseView proposed = as(f.responsible(), () -> treatments.proposeChange(f.course().id(), new ChangeCommand(1, "Add a third visit", plan(f.template().serviceId(), f.base().serviceId(), BigDecimal.valueOf(1500), 3))));
        Long changeId = proposed.changes().getFirst().id();
        assertThrows(BusinessException.class, () -> as(f.base().customer(), () -> treatments.settlement(f.course().id(), changeId)));
        as(f.base().admin(), () -> treatments.reviewChange(f.course().id(), changeId, new DecisionCommand(1, "Approved"), true));
        CourseView accepted = as(f.base().customer(), () -> treatments.customerChange(f.course().id(), changeId, new DecisionCommand(1, null), true));
        assertEquals(1, accepted.version()); assertEquals("AWAITING_SETTLEMENT", accepted.changes().getFirst().status());
        var order = as(f.base().customer(), () -> treatments.settlement(f.course().id(), changeId));
        assertThrows(BusinessException.class, () -> as(f.base().customer(), () -> treatments.applySettledChange(f.course().id(), changeId)));
        pay(f.base(), order.getOrderId());
        CourseView applied = as(f.base().customer(), () -> treatments.course(f.course().id())); assertEquals(2, applied.version()); assertEquals(3, applied.sessions().size());
        assertEquals(applied.version(), as(f.base().customer(), () -> treatments.applySettledChange(f.course().id(), changeId)).version());
        tx(() -> {
            assertEquals(3, tickets.findById(f.course().ticketId()).orElseThrow().getTotalSessions());
            assertEquals(TicketStatus.REVOKED, tickets.findByOrderIdForUpdate(order.getOrderId()).orElseThrow().getStatus()); return null;
        });
    }

    @Test void simultaneousBookingOfOneCourseItemCommitsOnlyOneAppointment() throws Exception {
        CourseFixture f = courseFixture(); assignAndIntake(f);
        var result = concurrently(() -> attempt(() -> as(f.base().customer(), () -> appointments.bookAppointment(courseBooking(f, f.course().sessions().getFirst(), LocalTime.of(10, 0))))),
                () -> attempt(() -> as(f.base().customer(), () -> appointments.bookAppointment(courseBooking(f, f.course().sessions().getFirst(), LocalTime.of(12, 0))))));
        assertEquals(1, result.stream().filter(Attempt::accepted).count());
        assertEquals(1, tickets.findById(f.course().ticketId()).orElseThrow().getReservedSessions());
        assertEquals(1L, tx(() -> entityManager.createQuery("select count(a) from AppointmentItem a where a.treatmentSessionItemId=:sid", Long.class)
                .setParameter("sid", f.course().sessions().getFirst().items().getFirst().id()).getSingleResult()));
        CourseView committed = as(f.base().customer(), () -> treatments.course(f.course().id()));
        assertEquals("SCHEDULED", committed.sessions().getFirst().items().getFirst().status());
        assertNotNull(committed.sessions().getFirst().items().getFirst().appointmentItemId());
    }

    @Test void forfeitedNoShowNeverIncreasesClinicalProgressOrBecomesRefundableThroughRepricing() {
        CourseFixture f = courseFixture(); assignAndIntake(f); long appointmentId = bookCourse(f);
        UpdateAppointmentStatusRequest confirmed = new UpdateAppointmentStatusRequest(); confirmed.setStatus(AppointmentStatus.CONFIRMED);
        as(f.base().admin(), () -> appointments.updateAppointmentStatus(appointmentId, confirmed));
        tx(() -> { appointmentRows.findById(appointmentId).orElseThrow().setAppointmentDate(LocalDate.now(SpaTimeRules.ZONE).minusDays(1)); return null; });
        UpdateAppointmentStatusRequest absent = new UpdateAppointmentStatusRequest(); absent.setStatus(AppointmentStatus.NO_SHOW); absent.setNotes("Customer did not attend");
        as(f.base().admin(), () -> appointments.updateAppointmentStatus(appointmentId, absent));
        CourseView course = as(f.base().customer(), () -> treatments.course(f.course().id())); assertEquals(0, course.completedSessions()); assertEquals("PLANNED", course.sessions().getFirst().status());
        assertThrows(BusinessException.class, () -> as(f.responsible(), () -> treatments.proposeChange(f.course().id(), new ChangeCommand(1, "Cannot refund consumed quota", plan(f.template().serviceId(), f.base().serviceId(), BigDecimal.valueOf(400), 2)))));
        CourseView request = as(f.base().customer(), () -> treatments.request(f.course().id(), new RequestCommand("CANCEL", "Stop remaining treatment", null)));
        CourseView canceled = as(f.base().admin(), () -> treatments.reviewRequest(f.course().id(), request.requests().getFirst().id(), new DecisionCommand(1, "Refund unused treatment"), true));
        assertEquals(0, BigDecimal.valueOf(500).compareTo(canceled.requests().getFirst().refundAmount()));
        tx(() -> {
            Course stored = entityManager.find(Course.class, f.course().id()); assertEquals(0, BigDecimal.valueOf(500).compareTo(stored.getConsumedAmount()));
            assertEquals(1L, entityManager.createQuery("select count(f) from TreatmentFinancialConsumption f where f.courseId=:cid", Long.class).setParameter("cid", stored.getId()).getSingleResult()); return null;
        });
    }

    @Test void contiguousFortyMinuteComponentsHaveSlotsAndReschedulingExcludesTheirOwnAppointment() {
        CourseFixture f = courseFixture(); assignAndIntake(f);
        long secondService = tx(() -> {
            BeautyService svc = services.save(BeautyService.builder().name("Second component").slug(UUID.randomUUID().toString()).basePrice(BigDecimal.valueOf(100)).durationMinutes(30).preparationTimeMinutes(10).build());
            skills.save(StaffServiceSkill.builder().staff(staffs.getReferenceById(f.base().firstStaffId())).service(svc).isCertified(true).build()); return svc.getId();
        });
        Plan combined = new Plan("Two components", "A contiguous session", f.template().serviceId(), BigDecimal.valueOf(1000), 30, "Accepted terms",
                List.of(new PlanSession(1, "Combined session", 0, null, true, List.of(new PlanItem(f.base().serviceId(), null, true), new PlanItem(secondService, null, true)))));
        CourseView proposed = as(f.responsible(), () -> treatments.proposeChange(f.course().id(), new ChangeCommand(1, "Combine services into one visit", combined)));
        Long change = proposed.changes().getFirst().id(); as(f.base().admin(), () -> treatments.reviewChange(f.course().id(), change, new DecisionCommand(1, "Approved"), true));
        CourseView applied = as(f.base().customer(), () -> treatments.customerChange(f.course().id(), change, new DecisionCommand(1, null), true));
        Long sessionId = applied.sessions().getFirst().id();
        assertTrue(as(f.base().customer(), () -> treatments.availableSlots(f.course().id(), sessionId, f.base().date(), f.base().firstStaffId())).contains("10:00"));
        BookAppointmentRequest command = new BookAppointmentRequest(); command.setAppointmentDate(f.base().date()); command.setStartTime(LocalTime.of(10, 0));
        command.setItems(applied.sessions().getFirst().items().stream().map(item -> {
            AppointmentItemRequest component = new AppointmentItemRequest(); component.setServiceId(item.serviceId()); component.setStaffId(f.base().firstStaffId()); component.setTicketId(f.course().ticketId()); component.setTreatmentSessionItemId(item.id()); return component;
        }).toList());
        Long appointmentId = as(f.base().customer(), () -> appointments.bookAppointment(command)).getId();
        assertTrue(as(f.base().customer(), () -> treatments.availableSlots(f.course().id(), sessionId, f.base().date(), f.base().firstStaffId(), appointmentId)).contains("10:00"));
        assertFalse(as(f.base().customer(), () -> treatments.availableSlots(f.course().id(), sessionId, f.base().date(), f.base().firstStaffId())).contains("10:00"));
    }

    @Test void incidentDuringActiveExecutionPersistsItsHoldWithoutCancelingTheActiveAppointment() {
        CourseFixture f = courseFixture(); assignAndIntake(f); Long appointmentId = bookCourse(f);
        withPermissions(f.responsible(), "SPA_EXECUTION");
        CourseView clinical = as(f.responsible(), () -> treatments.course(f.course().id()));
        assertTrue(clinical.allowedActions().contains("REQUEST_INCIDENT"));
        assertFalse(clinical.allowedActions().contains("REQUEST_CANCEL"));
        assertFalse(clinical.allowedActions().contains("REQUEST_EXTEND"));
        assertThrows(AccessDeniedException.class, () -> as(f.responsible(), () -> treatments.request(f.course().id(), new RequestCommand("CANCEL", "Execution cannot cancel financial rights", null))));
        assertThrows(AccessDeniedException.class, () -> as(f.responsible(), () -> treatments.request(f.course().id(), new RequestCommand("EXTEND", "Execution cannot extend financial rights", 10))));
        as(f.base().admin(), () -> tx(() -> {
            Appointment active = appointmentRows.findById(appointmentId).orElseThrow(); active.setStatus(AppointmentStatus.IN_PROGRESS);
            active.getItems().getFirst().setExecutionStatus(AppointmentItemExecutionStatus.IN_PROGRESS);
            active.getItems().getFirst().setActualStartedAt(Instant.now()); treatmentAppointments.synchronizeExecution(active.getItems().getFirst()); return null;
        }));
        CourseView held = as(f.responsible(), () -> treatments.request(f.course().id(), new RequestCommand("INCIDENT", "Reaction reported during treatment", null)));
        assertEquals("PENDING_REVIEW", held.status());
        assertEquals("PENDING_APPROVAL", held.requests().getFirst().status());
        assertEquals("INCIDENT", held.requests().getFirst().type());
        assertEquals(AppointmentStatus.IN_PROGRESS, appointmentRows.findById(appointmentId).orElseThrow().getStatus());
        assertEquals(1, tickets.findById(f.course().ticketId()).orElseThrow().getReservedSessions());
        assertThrows(BusinessException.class, () -> as(f.responsible(), () -> treatments.request(f.course().id(), new RequestCommand("INCIDENT", "Same pending incident", null))));
        CourseView unchanged = as(f.responsible(), () -> treatments.course(f.course().id()));
        assertEquals("PENDING_REVIEW", unchanged.status()); assertEquals(1, unchanged.requests().size());
        as(f.base().admin(), () -> treatments.reviewRequest(f.course().id(), held.requests().getFirst().id(), new DecisionCommand(1, "Responsible staff review required"), true));
        assertEquals(AppointmentStatus.IN_PROGRESS, appointmentRows.findById(appointmentId).orElseThrow().getStatus());
        assertThrows(BusinessException.class, () -> as(f.base().admin(), () -> tx(() -> {
            treatmentAppointments.validateAppointmentStart(appointmentRows.findById(appointmentId).orElseThrow()); return null;
        })));
    }
}
