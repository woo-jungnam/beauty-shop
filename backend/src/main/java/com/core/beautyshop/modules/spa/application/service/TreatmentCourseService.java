package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.identity.api.IdentityFacade;
import com.core.beautyshop.modules.order.api.OrderFacade;
import com.core.beautyshop.modules.order.api.dto.CreateSpaPackageOrderCommand;
import com.core.beautyshop.modules.order.api.dto.SpaPackageOrderResult;
import com.core.beautyshop.modules.spa.domain.*;
import com.core.beautyshop.modules.spa.domain.enums.TicketStatus;
import com.core.beautyshop.modules.spa.domain.treatment.TreatmentEntities.*;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import static com.core.beautyshop.modules.spa.application.service.TreatmentContracts.*;

@Service @RequiredArgsConstructor @Transactional
public class TreatmentCourseService {
    @org.springframework.beans.factory.annotation.Autowired
    private SpaLoyaltyService loyalty;
    private final SpaBusinessCalendarService calendar;
    @PersistenceContext private EntityManager em;
    private final ObjectMapper json;
    private final BeautyServiceRepository services;
    private final ServicePackageRepository packages;
    private final SpaPurchaseSnapshotRepository snapshots;
    private final UserServiceTicketRepository tickets;
    private final StaffRepository staff;
    private final IdentityFacade identity;
    private final OrderFacade orders;
    private final SpaBookingPolicyService policies;
    private final SpaAccessService access;
    private final BeautyServiceService catalog;
    private final TreatmentNotificationService notifications;
    private final org.springframework.context.ApplicationEventPublisher events;
    private final AppointmentRepository appointmentRows;
    private final StaffScheduleRepository treatmentShifts;
    private final FacilitySchedulingService treatmentFacilities;
    @Autowired @Lazy private AppointmentService appointments;
    @org.springframework.beans.factory.annotation.Value("${app.spa.treatment.sales-enabled:true}")
    private boolean treatmentSalesEnabled = true;

    public List<TemplateView> templates(boolean admin) {
        if (admin) requireAdmin();
        return list(Template.class, "select t from TreatmentTemplate t order by t.id desc").stream()
                .filter(t -> admin || (!t.isArchived() && t.getPublishedVersionId() != null))
                .map(t -> templateView(t, admin)).toList();
    }

    public TemplateView template(Long id, boolean admin) {
        if (admin) requireAdmin();
        Template t = get(Template.class, id);
        if (!admin && (t.isArchived() || t.getPublishedVersionId() == null)) throw missing("Template");
        return templateView(t, admin);
    }

    public TemplateView saveTemplate(Long id, Plan command) {
        requireAdmin(); Plan plan = validatedPlan(command);
        Template t;
        TemplateVersion v;
        if (id == null) {
            t = new Template(); t.setServiceId(plan.serviceId()); persist(t);
            v = new TemplateVersion(); v.setTemplateId(t.getId()); v.setVersionNumber(1); v.setPlanJson(write(plan)); persist(v, false);
        } else {
            t = lock(Template.class, id);
            if (t.getDraftVersionId() == null) throw business("Create a new version before editing a published plan");
            v = lock(TemplateVersion.class, t.getDraftVersionId());
            if (!"DRAFT".equals(v.getStatus())) throw business("Published versions are immutable");
            if (!Objects.equals(t.getServiceId(), plan.serviceId())) throw business("A template's parent service cannot change");
        }
        v.setPlanJson(write(plan)); em.flush(); t.setDraftVersionId(v.getId());
        return templateView(t, true);
    }

    public TemplateView newVersion(Long id, Plan command) {
        requireAdmin(); Template t = lock(Template.class, id);
        if (t.getDraftVersionId() != null) throw business("This template already has a draft");
        TemplateVersion old = get(TemplateVersion.class, t.getPublishedVersionId());
        Plan plan = command == null ? read(old.getPlanJson()) : validatedPlan(command);
        if (!Objects.equals(t.getServiceId(), plan.serviceId())) throw business("A template's parent service cannot change");
        TemplateVersion v = new TemplateVersion(); v.setTemplateId(id); v.setVersionNumber(old.getVersionNumber() + 1);
        v.setPlanJson(write(plan)); persist(v); t.setDraftVersionId(v.getId());
        return templateView(t, true);
    }

    public TemplateView publish(Long id) {
        requireAdmin(); Template t = lock(Template.class, id);
        if (t.getDraftVersionId() == null) throw business("No draft is available to publish");
        TemplateVersion v = lock(TemplateVersion.class, t.getDraftVersionId());
        Plan plan = validatedPlan(read(v.getPlanJson()));
        v.setPlanJson(write(plan)); v.setPackageId(createManagedPackage(plan, plan.price()).getId());
        v.setStatus("PUBLISHED"); v.setPublishedAt(Instant.now());
        t.setPublishedVersionId(v.getId()); t.setDraftVersionId(null); t.setArchived(false);
        return templateView(t, true);
    }

    public TemplateView archive(Long id) {
        requireAdmin(); Template t = lock(Template.class, id); t.setArchived(true); return templateView(t, true);
    }

    public SpaPackageOrderResult purchase(PurchaseCommand command) {
        if (command == null || command.templateId() == null || !Boolean.TRUE.equals(command.acceptedTerms())
                || command.idempotencyKey() == null || command.idempotencyKey().isBlank() || command.idempotencyKey().length() > 64)
            throw business("Published plan, accepted terms and an idempotency key are required");
        Long userId = SecurityUtils.getCurrentUserId();
        Template template = lock(Template.class, command.templateId());
        String hash = hash(writeObject(command));
        Purchase old = first(Purchase.class, "select p from TreatmentPurchase p where p.userId=:uid and p.idempotencyKey=:key", "uid", userId, "key", command.idempotencyKey());
        if (old != null) {
            if (!old.getRequestHash().equals(hash)) throw business("Idempotency key was already used for a different purchase");
            TemplateVersion oldVersion = get(TemplateVersion.class, old.getVersionId());
            return orderFor(userId, oldVersion.getPackageId(), read(old.getPlanJson()), command.notes(), purchaseKey(userId, command.idempotencyKey()));
        }
        if (!treatmentSalesEnabled) throw business("Việc bán lộ trình mới đang tạm dừng; hồ sơ đã mua vẫn được phục vụ");
        if (template.isArchived() || template.getPublishedVersionId() == null) throw business("This treatment is not available for purchase");
        if (command.versionId() == null || !command.versionId().equals(template.getPublishedVersionId()))
            throw business("The published plan has changed; review and accept its latest terms");
        TemplateVersion version = get(TemplateVersion.class, template.getPublishedVersionId());
        Plan plan = validatedPlan(read(version.getPlanJson()));
        SpaPackageOrderResult result = orderFor(userId, version.getPackageId(), plan, command.notes(), purchaseKey(userId, command.idempotencyKey()));
        savePurchaseSnapshot(result.getOrderId(), version.getPackageId(), plan, result.getTotalAmount());
        Purchase p = new Purchase(); p.setOrderId(result.getOrderId()); p.setUserId(userId); p.setTemplateId(template.getId());
        p.setVersionId(version.getId()); p.setPlanJson(write(plan)); p.setIdempotencyKey(command.idempotencyKey()); p.setRequestHash(hash); persist(p);
        return result;
    }

    public void onTicketIssued(UserServiceTicket ticket) {
        if (ticket == null || ticket.getOrderId() == null) return;
        Purchase purchase = em.find(Purchase.class, ticket.getOrderId(), LockModeType.PESSIMISTIC_WRITE);
        if (purchase != null) {
            Course previous = first(Course.class, "select c from TreatmentCourse c where c.orderId=:oid", "oid", ticket.getOrderId());
            if (previous != null) { ticket.setTreatmentCourseId(previous.getId()); return; }
            if (!Objects.equals(ticket.getUserId(), purchase.getUserId()) || !orders.isPaidOrderForUser(ticket.getOrderId(), ticket.getUserId()))
                throw business("Treatment payment ownership does not match");
            Plan plan = read(purchase.getPlanJson());
            Course c = new Course(); c.setUserId(ticket.getUserId()); c.setTemplateId(purchase.getTemplateId());
            c.setVersionId(purchase.getVersionId()); c.setOrderId(ticket.getOrderId()); c.setTicketId(ticket.getId());
            c.setPlanJson(purchase.getPlanJson()); c.setPaidAmount(plan.price().setScale(0, RoundingMode.HALF_UP));
            c.setExpiryDate(ticket.getExpiryDate());
            if (expired(c)) c.setStatus("EXPIRED");
            persist(c); ticket.setTreatmentCourseId(c.getId()); createSessions(c, plan); audit(c, "PURCHASE_PAID", "Purchased published plan version " + purchase.getVersionId());
            return;
        }
        Change change = first(Change.class, "select c from TreatmentChange c where c.settlementOrderId=:oid", "oid", ticket.getOrderId());
        if (change != null) {
            ticket.setStatus(TicketStatus.REVOKED);
            events.publishEvent(new com.core.beautyshop.modules.spa.api.event.TreatmentSettlementPaidEvent(change.getId()));
        }
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void reconcilePaidSettlement(Long changeId) {
        Change observed = get(Change.class, changeId); Course c = lock(Course.class, observed.getCourseId());
        Change change = get(Change.class, changeId);
        if (!"AWAITING_SETTLEMENT".equals(change.getStatus()) || change.getSettlementOrderId() == null
                || !orders.isPaidOrderForUser(change.getSettlementOrderId(), c.getUserId())) return;
        if (Set.of("COMPLETED", "CANCELLED", "REFUNDED").contains(c.getStatus())) {
            queueUnappliedSettlementRefund(c, change); return;
        }
        if (!hasLiveBookings(c) && !expired(c)) applyChange(c, change);
        else audit(c, "SETTLEMENT_PAID", "Adjustment paid; resolve future bookings or validity before applying revision");
    }

    public List<CourseView> myCourses() {
        return list(Course.class, "select c from TreatmentCourse c where c.userId=:uid order by c.id desc", "uid", SecurityUtils.getCurrentUserId())
                .stream().map(c -> courseView(c, false)).toList();
    }

    public List<CourseView> operationalCourses() {
        requireOperator();
        return list(Course.class, "select c from TreatmentCourse c order by c.id desc").stream()
                .filter(c -> access.canManageReception() || (access.permission("SPA_EXECUTION") && isAssigned(c)))
                .map(c -> courseView(c, access.hasRole("ADMIN") || (access.permission("SPA_EXECUTION") && isAssigned(c)))).toList();
    }

    public CourseView course(Long id) {
        Course c = get(Course.class, id); requireView(c);
        return courseView(c, access.hasRole("ADMIN") || (access.permission("SPA_EXECUTION") && isAssigned(c)));
    }

    public CourseView assign(Long id, AssignCommand command) {
        if (!access.canManageReception()) throw denied();
        Course c = lock(Course.class, id);
        if (command == null || command.staffId() == null) throw business("Assigned staff is required");
        Staff target = staff.findById(command.staffId()).filter(s -> Boolean.TRUE.equals(s.getIsActive()) && !Boolean.TRUE.equals(s.getIsDeleted()))
                .orElseThrow(() -> business("Assigned staff is unavailable"));
        c.setAssignedStaffId(target.getId()); audit(c, "ASSIGNED", reasonOr(command.reason(), "Staff assignment") + "; staffId=" + target.getId());
        return courseView(c, access.hasRole("ADMIN") || (access.permission("SPA_EXECUTION") && isAssigned(c)));
    }

    public CourseView intake(Long id, IntakeCommand command) {
        Course c = lock(Course.class, id); requireClinical(c);
        if (!"PENDING_INTAKE".equals(c.getStatus()) || expired(c)) throw business("Only a valid pending intake can be accepted");
        c.setIntakeNotes(text(command == null ? null : command.notes(), "Intake assessment", 4000));
        c.setStatus("ACTIVE"); audit(c, "INTAKE_ACCEPTED", "Responsible staff accepted the purchased plan");
        return courseView(c, true);
    }

    public CourseView proposeChange(Long id, ChangeCommand command) {
        Course c = lock(Course.class, id); requireClinical(c); requireOpen(c);
        if (command == null) throw business("Change request is required");
        expected(c, command.expectedVersion());
        if (list(Change.class, "select x from TreatmentChange x where x.courseId=:cid and x.status in ('PENDING_APPROVAL','AWAITING_CUSTOMER','AWAITING_SETTLEMENT')", "cid", id).size() > 0)
            throw business("A plan change is already pending");
        Plan plan = validatedPlan(command.plan());
        if (!Objects.equals(plan.serviceId(), read(c.getPlanJson()).serviceId())) throw business("The parent treatment service cannot change");
        validateAmendment(c, plan);
        Change change = new Change(); change.setCourseId(id); change.setBaseRevision(c.getPlanRevision());
        change.setPlanJson(write(plan)); change.setOldPlanJson(c.getPlanJson());
        change.setReason(text(command.reason(), "Change reason", 1000)); change.setProposedBy(SecurityUtils.getCurrentUserId());
        change.setPriceDifference(plan.price().setScale(0, RoundingMode.HALF_UP).subtract(read(c.getPlanJson()).price().setScale(0, RoundingMode.HALF_UP)));
        persist(change); audit(c, "CHANGE_PROPOSED", "Change " + change.getId() + ": " + change.getReason()); return courseView(c, true);
    }

    public CourseView reviewChange(Long id, Long changeId, DecisionCommand command, boolean approve) {
        requireAdmin(); Course c = lock(Course.class, id); Change change = change(c, changeId);
        requireOpen(c);
        if (!"PENDING_APPROVAL".equals(change.getStatus())) throw business("This change is not awaiting approval");
        if (change.getBaseRevision() != c.getPlanRevision()) throw business("The plan changed; submit a new proposal");
        change.setStatus(approve ? "AWAITING_CUSTOMER" : "REJECTED"); change.setApprovedBy(SecurityUtils.getCurrentUserId());
        audit(c, approve ? "CHANGE_APPROVED" : "CHANGE_REJECTED", "Change " + changeId + "; " + reason(command)); return courseView(c, true);
    }

    public CourseView customerChange(Long id, Long changeId, DecisionCommand command, boolean accept) {
        Course c = lock(Course.class, id); requireOwner(c); Change change = change(c, changeId);
        if (command == null || command.expectedVersion() == null || command.expectedVersion() != change.getBaseRevision())
            throw business("Review the current version before accepting this change");
        if (accept && Objects.equals(change.getAcceptedBy(), c.getUserId()) && change.getAcceptedAt() != null) {
            if (appliedRevisionMatches(c, change) || ("AWAITING_SETTLEMENT".equals(change.getStatus())
                    && c.getPlanRevision() == change.getBaseRevision() && Objects.equals(c.getPlanJson(), change.getOldPlanJson())
                    && change.getApprovedBy() != null)) return courseView(c, false);
            throw business("The accepted treatment version is no longer current; reload this treatment");
        }
        requireOpen(c);
        if (!"AWAITING_CUSTOMER".equals(change.getStatus())) throw business("This change is not awaiting customer confirmation");
        if (change.getBaseRevision() != c.getPlanRevision()) throw business("The plan changed; reload this treatment");
        if (!accept) change.setStatus("REJECTED");
        else {
            change.setAcceptedBy(SecurityUtils.getCurrentUserId()); change.setAcceptedAt(Instant.now());
            if (change.getPriceDifference().signum() > 0) change.setStatus("AWAITING_SETTLEMENT");
            else {
                if (hasLiveBookings(c)) throw business("Cancel future treatment bookings before applying a changed plan");
                applyChange(c, change);
            }
        }
        audit(c, accept ? "CUSTOMER_ACCEPTED_CHANGE" : "CUSTOMER_REJECTED_CHANGE", "Change " + changeId); return courseView(c, false);
    }

    public SpaPackageOrderResult settlement(Long id, Long changeId) {
        Course c = lock(Course.class, id); requireOwner(c); Change change = change(c, changeId);
        requireOpen(c);
        if (!"AWAITING_SETTLEMENT".equals(change.getStatus()) || change.getAcceptedAt() == null) throw business("No accepted adjustment payment is due");
        Plan proposed = read(change.getPlanJson());
        ServicePackage pack;
        if (change.getSettlementOrderId() == null) {
            pack = createManagedPackage(proposed, change.getPriceDifference());
        } else {
            var snap = snapshots.findById(change.getSettlementOrderId()).orElseThrow(() -> business("Missing adjustment snapshot"));
            pack = packages.getReferenceById(snap.getPackageId());
        }
        Plan paymentPlan = new Plan(proposed.name() + " - adjustment", proposed.description(), proposed.serviceId(), change.getPriceDifference(), proposed.validityDays(), proposed.terms(), proposed.sessions());
        SpaPackageOrderResult result = orderFor(c.getUserId(), pack.getId(), paymentPlan, "Treatment plan adjustment " + change.getId(), hash("treatment-change:" + change.getId()));
        savePurchaseSnapshot(result.getOrderId(), pack.getId(), paymentPlan, result.getTotalAmount()); change.setSettlementOrderId(result.getOrderId()); return result;
    }

    public CourseView applySettledChange(Long id, Long changeId) {
        Course c = lock(Course.class, id); requireView(c); Change change = change(c, changeId);
        if ("APPLIED".equals(change.getStatus())) {
            if (appliedRevisionMatches(c, change)) return courseView(c, access.hasRole("ADMIN") || (access.permission("SPA_EXECUTION") && isAssigned(c)));
            throw business("The applied treatment version is no longer current; reload this treatment");
        }
        if (!"AWAITING_SETTLEMENT".equals(change.getStatus()) || change.getSettlementOrderId() == null
                || !orders.isPaidOrderForUser(change.getSettlementOrderId(), c.getUserId())) throw business("Adjustment payment is not settled");
        if (hasLiveBookings(c)) throw business("Cancel future treatment bookings before applying this revision");
        applyChange(c, change); return courseView(c, access.hasRole("ADMIN") || (access.permission("SPA_EXECUTION") && isAssigned(c)));
    }

    public CourseView request(Long id, RequestCommand command) {
        Course c = lock(Course.class, id); requireView(c);
        if (command == null || command.type() == null || !Set.of("PAUSE", "RESUME", "CANCEL", "EXTEND", "INCIDENT").contains(command.type())) throw business("Unknown treatment request type");
        boolean assignedIncident = "INCIDENT".equals(command.type()) && access.permission("SPA_EXECUTION") && isAssigned(c);
        if (!Objects.equals(c.getUserId(), SecurityUtils.getCurrentUserId()) && !access.canManageReception() && !assignedIncident) throw denied();
        if (Set.of("COMPLETED", "CANCELLED", "REFUNDED").contains(c.getStatus())) throw business("This treatment is closed");
        if ("EXTEND".equals(command.type()) && (command.extensionDays() == null || command.extensionDays() < 1 || command.extensionDays() > 3650))
            throw business("Extension days must be between 1 and 3650");
        if (first(WorkflowRequest.class, "select r from TreatmentWorkflowRequest r where r.courseId=:cid and r.status='PENDING_APPROVAL' and r.type=:type", "cid", id, "type", command.type()) != null)
            throw business("A request of this type is already awaiting approval");
        WorkflowRequest r = new WorkflowRequest(); r.setCourseId(id); r.setType(command.type());
        r.setReason(text(command.reason(), "Request reason", 1000)); r.setExtensionDays(command.extensionDays()); r.setRequestedBy(SecurityUtils.getCurrentUserId()); persist(r);
        if ("INCIDENT".equals(command.type())) {
            c.setStatus("PENDING_REVIEW");
            cancelFutureBookings(c, true);
        }
        audit(c, "REQUEST_CREATED", command.type() + ": " + r.getReason()); return courseView(c, access.hasRole("ADMIN") || (access.permission("SPA_EXECUTION") && isAssigned(c)));
    }

    public CourseView reviewRequest(Long id, Long requestId, DecisionCommand command, boolean approve) {
        requireAdmin(); Course c = lock(Course.class, id); WorkflowRequest r = workflow(c, requestId);
        if (!"PENDING_APPROVAL".equals(r.getStatus())) throw business("This request has already been reviewed");
        if (Set.of("COMPLETED", "CANCELLED", "REFUNDED").contains(c.getStatus())) throw business("Closed treatment requests cannot change the course state");
        r.setReviewReason(text(reason(command), "Review reason", 1000)); r.setReviewedBy(SecurityUtils.getCurrentUserId()); r.setReviewedAt(Instant.now());
        if (!approve) r.setStatus("REJECTED");
        else {
            switch (r.getType()) {
                case "PAUSE", "INCIDENT" -> { requireOpen(c); cancelFutureBookings(c, "INCIDENT".equals(r.getType())); c.setStatus("INCIDENT".equals(r.getType()) ? "PENDING_REVIEW" : "PAUSED"); }
                case "RESUME" -> {
                    if (!Set.of("PAUSED", "PENDING_REVIEW").contains(c.getStatus()) || expired(c)) throw business("A valid paused treatment is required");
                    c.setStatus(c.getIntakeNotes() == null ? "PENDING_INTAKE" : "ACTIVE");
                }
                case "EXTEND" -> {
                    if (c.getExpiryDate() == null) throw business("This treatment already has unlimited validity");
                    c.setExpiryDate((c.getExpiryDate().isAfter(Instant.now()) ? c.getExpiryDate() : Instant.now()).plus(r.getExtensionDays(), ChronoUnit.DAYS));
                    UserServiceTicket ticket = tickets.findByIdForUpdate(c.getTicketId()).orElseThrow(() -> missing("Ticket")); ticket.setExpiryDate(c.getExpiryDate());
                    if (ticket.getStatus() == TicketStatus.EXPIRED) ticket.setStatus(TicketStatus.ACTIVE);
                    if ("EXPIRED".equals(c.getStatus())) c.setStatus(c.getIntakeNotes() == null ? "PENDING_INTAKE" : "ACTIVE");
                }
                case "CANCEL" -> {
                    cancelFutureBookings(c); c.setStatus("CANCELLED");
                    UserServiceTicket ticket = tickets.findByIdForUpdate(c.getTicketId()).orElseThrow(() -> missing("Ticket")); ticket.setStatus(TicketStatus.REVOKED);
                    r.setRefundAmount(refundable(c));
                    for (Change pending : list(Change.class, "select x from TreatmentChange x where x.courseId=:cid and x.status='AWAITING_SETTLEMENT' and x.settlementOrderId is not null", "cid", id)) {
                        if (orders.isPaidOrderForUser(pending.getSettlementOrderId(), c.getUserId())) queueUnappliedSettlementRefund(c, pending);
                    }
                }
                default -> throw business("Unsupported request type");
            }
            r.setStatus(r.getRefundAmount().signum() > 0 ? "AWAITING_REFUND" : "APPROVED");
        }
        audit(c, approve ? "REQUEST_APPROVED" : "REQUEST_REJECTED", r.getType() + "; " + r.getReviewReason()); return courseView(c, true);
    }

    public CourseView confirmRefund(Long id, Long requestId, RefundCommand command) {
        requireAdmin(); Course c = lock(Course.class, id); WorkflowRequest r = workflow(c, requestId);
        if (command == null || command.amount() == null || command.amount().compareTo(r.getRefundAmount()) != 0
                || command.reference() == null || command.reference().isBlank()) throw business("Refund amount must equal the approved amount; reference is required");
        if ("REFUNDED".equals(r.getStatus()) && Objects.equals(r.getRefundReference(), command.reference())) return courseView(c, true);
        if (!"AWAITING_REFUND".equals(r.getStatus())) throw business("This request is not awaiting a refund confirmation");
        List<Long> sources = new ArrayList<>(); sources.add(c.getOrderId());
        list(Change.class, "select x from TreatmentChange x where x.courseId=:cid and x.settlementOrderId is not null", "cid", id).stream()
                .map(Change::getSettlementOrderId).filter(oid -> orders.isPaidOrderForUser(oid, c.getUserId())).forEach(sources::add);
        orders.confirmTreatmentRefund(sources, command.amount(), command.reference(), text(command.reason(), "Refund reason", 250));
        r.setStatus("REFUNDED"); r.setRefundReference(command.reference()); if ("CANCEL".equals(r.getType())) c.setStatus("REFUNDED");
        audit(c, "REFUND_CONFIRMED", "Refund reference " + command.reference() + "; amount=" + command.amount()); return courseView(c, true);
    }

    public CourseView sessionResult(Long id, Long sessionId, ResultCommand command) {
        Course c = lock(Course.class, id); requireClinical(c); Session session = session(c, sessionId);
        if (command == null) throw business("Result is required");
        if (!items(session).stream().anyMatch(i -> "COMPLETED".equals(i.getStatus()))) throw business("Perform an appointment item before recording a treatment result");
        session.setResult(text(command.result(), "Session result", 4000)); session.setAftercare(optionalText(command.aftercare(), 4000));
        session.setInternalNotes(optionalText(command.internalNotes(), 4000)); session.setSharedWithCustomer(Boolean.TRUE.equals(command.sharedWithCustomer()));
        SessionResult evidence = new SessionResult(); evidence.setSessionId(sessionId); evidence.setAuthorId(SecurityUtils.getCurrentUserId());
        evidence.setResult(session.getResult()); evidence.setAftercare(session.getAftercare()); evidence.setInternalNotes(session.getInternalNotes()); evidence.setSharedWithCustomer(session.isSharedWithCustomer()); persist(evidence);
        refreshSession(session); audit(c, "SESSION_RESULT", "Result recorded for session " + sessionId); return courseView(c, true);
    }

    public CourseView complete(Long id, CompleteCommand command) {
        Course c = lock(Course.class, id); requireClinical(c);
        if (!"ACTIVE".equals(c.getStatus()) || expired(c)) throw business("Only an active valid course can be completed");
        if (sessions(c).stream().anyMatch(s -> s.isRequired() && !Set.of("COMPLETED", "WAIVED").contains(s.getStatus())))
            throw business("Every required treatment session must be completed with a result");
        if (hasLiveBookings(c)) throw business("Resolve all open treatment appointments before completion");
        c.setSummary(text(command == null ? null : command.summary(), "Completion summary", 4000)); c.setStatus("COMPLETED"); c.setCompletedAt(Instant.now());
        audit(c, "COURSE_COMPLETED", "Responsible staff completed the treatment course"); return courseView(c, true);
    }

    public List<String> availableSlots(Long id, Long sessionId, LocalDate date, Long staffId) {
        return availableSlots(id, sessionId, date, staffId, null);
    }

    public List<String> availableSlots(Long id, Long sessionId, LocalDate date, Long staffId, Long appointmentId) {
        Course c = get(Course.class, id); requireView(c); Session s = session(c, sessionId);
        if (date == null) throw business("Date is required");
        if (date.isBefore(LocalDate.now(SpaTimeRules.ZONE))) throw business("Appointment date cannot be in the past");
        Appointment existing = appointmentId == null ? null : appointmentRows.findByIdWithItems(appointmentId)
                .filter(a -> !Boolean.TRUE.equals(a.getIsDeleted()) && Objects.equals(a.getUserId(), c.getUserId())
                        && Set.of("PENDING", "CONFIRMED").contains(a.getStatus().name()) && a.getCheckedInAt() == null)
                .orElseThrow(() -> business("This appointment cannot be rescheduled"));
        if (existing != null && items(s).stream().noneMatch(i -> existing.getItems().stream()
                .anyMatch(a -> Objects.equals(a.getId(), i.getAppointmentItemId())))) throw business("Appointment does not belong to this session");
        String blocked = blocked(c, s);
        if (blocked != null && !(existing != null && "This session already has a live appointment".equals(blocked))) return List.of();
        List<SessionItem> remaining = items(s).stream().filter(i -> !"COMPLETED".equals(i.getStatus()))
                .filter(i -> existing == null || existing.getItems().stream().anyMatch(a -> Objects.equals(a.getId(), i.getAppointmentItemId()))).toList();
        if (remaining.isEmpty()) return List.of();
        List<String> result = new ArrayList<>();
        var hours = calendar.forDate(date);
        if (hours.closed()) return List.of();
        for (int minute=hours.opensAt().getHour()*60+hours.opensAt().getMinute(); minute<hours.closesAt().getHour()*60+hours.closesAt().getMinute(); minute+=30) {
            LocalTime candidateTime=LocalTime.of(minute/60,minute%60);
            LocalTime cursor = candidateTime; String candidate = candidateTime.toString(); boolean valid = true;
            Instant firstStart = date.atTime(cursor).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
            if (!firstStart.isAfter(Instant.now()) || !withinWindow(c, s, firstStart)) continue;
            for (SessionItem item : remaining) {
                BeautyService service = services.findById(item.getServiceId()).orElseThrow(() -> missing("Service"));
                if (!Boolean.TRUE.equals(service.getIsActive()) || Boolean.TRUE.equals(service.getIsDeleted())) { valid = false; break; }
                AppointmentItem original = existing == null ? null : existing.getItems().stream()
                        .filter(a -> Objects.equals(a.getId(), item.getAppointmentItemId())).findFirst().orElseThrow();
                LocalTime end;
                try {
                    long duration = original == null ? SpaTimeRules.durationMinutes(service)
                            : Duration.between(original.getStartTime(), original.getEndTime()).toMinutes();
                    if (duration <= 0 || duration > Duration.between(cursor, hours.closesAt()).toMinutes()) { valid = false; break; }
                    end = cursor.plusMinutes(duration);
                }
                catch (BusinessException outsideHours) { valid = false; break; }
                LocalTime start = cursor;
                Long effectiveStaff = original != null && original.getStaff() != null ? original.getStaff().getId() : staffId;
                boolean hasPerformer = staff.findQualifiedActiveStaff(service.getId()).stream()
                        .filter(person -> !Boolean.TRUE.equals(service.getRequiresCertification()) || person.getSkills().stream().anyMatch(skill ->
                                skill.getService().getId().equals(service.getId()) && Boolean.TRUE.equals(skill.getIsCertified())
                                && (skill.getCertifiedUntil()==null || !skill.getCertifiedUntil().isBefore(date))))
                        .filter(person -> effectiveStaff == null || effectiveStaff.equals(person.getId()))
                        .anyMatch(person -> treatmentStaffAvailable(person.getId(), date, start, end, appointmentId));
                if (!hasPerformer || !treatmentFacilities.hasAvailability(service.getId(), date, start, end, appointmentId)) {
                    valid = false; break;
                }
                cursor = end;
            }
            boolean customerBusy = valid && (appointmentId == null
                    ? appointmentRows.existsOverlappingAppointmentForUser(c.getUserId(), date, LocalTime.parse(candidate), cursor)
                    : appointmentRows.existsOverlappingAppointmentForUserExcluding(c.getUserId(), appointmentId, date, LocalTime.parse(candidate), cursor));
            if (valid && !customerBusy)
                result.add(candidate);
        }
        return result;
    }

    private boolean treatmentStaffAvailable(Long staffId, LocalDate date, LocalTime start, LocalTime end, Long appointmentId) {
        boolean busy = appointmentId == null ? appointmentRows.existsOverlappingAppointmentForStaff(staffId, date, start, end)
                : appointmentRows.existsOverlappingAppointmentForStaffExcludingAppointment(staffId, date, start, end, appointmentId);
        if (busy) return false;
        return treatmentShifts.coversWorkingInterval(staffId, date, start, end);
    }

    public Report report() {
        requireAdmin(); List<Course> all = list(Course.class, "select c from TreatmentCourse c");
        Map<String, Long> counts = all.stream().collect(Collectors.groupingBy(this::effectiveStatus, LinkedHashMap::new, Collectors.counting()));
        long due = 0, unbooked = 0; BigDecimal unused = BigDecimal.ZERO;
        for (Course c : all) {
            if ("ACTIVE".equals(effectiveStatus(c))) {
                Session next = sessions(c).stream().filter(s -> !Set.of("COMPLETED", "WAIVED").contains(s.getStatus())).findFirst().orElse(null);
                if (next != null && !"SCHEDULED".equals(next.getStatus()) && !"IN_PROGRESS".equals(next.getStatus())) {
                    unbooked++; Instant earliest = earliest(c, next); if (earliest == null || !earliest.isAfter(Instant.now())) due++;
                }
            }
            unused = unused.add(refundable(c));
        }
        BigDecimal collected = all.stream().map(Course::getPaidAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refunded = list(WorkflowRequest.class, "select r from TreatmentWorkflowRequest r where r.status='REFUNDED'").stream().map(WorkflowRequest::getRefundAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Report(all.size(), counts.getOrDefault("PENDING_INTAKE", 0L), counts.getOrDefault("ACTIVE", 0L),
                counts.getOrDefault("PAUSED", 0L), counts.getOrDefault("COMPLETED", 0L), counts.getOrDefault("EXPIRED", 0L), due, unbooked, collected, refunded, unused, counts);
    }

    Course lockCourse(Long id) {
        Course course = get(Course.class, id);
        if (!writeLocked(course)) em.refresh(course, LockModeType.PESSIMISTIC_WRITE);
        return course;
    }
    SessionItem lockSessionItem(Long id) {
        SessionItem item = get(SessionItem.class, id);
        if (!writeLocked(item)) em.refresh(item, LockModeType.PESSIMISTIC_WRITE);
        return item;
    }
    private boolean writeLocked(Object entity) {
        LockModeType mode = em.getLockMode(entity);
        return mode == LockModeType.PESSIMISTIC_WRITE || mode == LockModeType.PESSIMISTIC_FORCE_INCREMENT;
    }
    SessionItem item(Long id) { return get(SessionItem.class, id); }
    Course courseForTicket(Long ticketId) { return first(Course.class, "select c from TreatmentCourse c where c.ticketId=:tid", "tid", ticketId); }
    List<Session> sessions(Course c) { return list(Session.class, "select s from TreatmentSession s where s.courseId=:cid and s.retired=false order by s.sequence", "cid", c.getId()); }
    List<SessionItem> items(Session s) { return list(SessionItem.class, "select i from TreatmentSessionItem i where i.sessionId=:sid order by i.id", "sid", s.getId()); }
    Session getSession(Long id) { return get(Session.class, id); }
    boolean withinWindow(Course c, Session s, Instant at) {
        Instant earliest = earliest(c, s), latest = latest(c, s);
        return (earliest == null || !at.isBefore(earliest)) && (latest == null || !at.isAfter(latest)) && (c.getExpiryDate() == null || at.isBefore(c.getExpiryDate()));
    }
    String blocked(Course c, Session s) {
        String state = effectiveStatus(c);
        if (!Set.of("ACTIVE", "PENDING_INTAKE").contains(state)) return "Treatment is " + state;
        if ("PENDING_INTAKE".equals(state) && s.getSequence() != 1) return "Intake must be accepted before later sessions";
        if (Set.of("COMPLETED", "WAIVED").contains(s.getStatus())) return "This session is already complete";
        if (sessions(c).stream().anyMatch(p -> p.isRequired() && p.getSequence() < s.getSequence() && !Set.of("COMPLETED", "WAIVED").contains(p.getStatus()))) return "Complete the preceding session first";
        if (items(s).stream().anyMatch(i -> Set.of("SCHEDULED", "IN_PROGRESS").contains(i.getStatus()))) return "This session already has a live appointment";
        return null;
    }
    void refreshSession(Session s) {
        List<SessionItem> all = items(s);
        boolean requiredComplete = all.stream().filter(SessionItem::isRequired).allMatch(i -> "COMPLETED".equals(i.getStatus()));
        if (requiredComplete && all.stream().anyMatch(i -> "COMPLETED".equals(i.getStatus())) && s.getResult() != null && !s.getResult().isBlank()) {
            s.setStatus("COMPLETED"); s.setCompletedAt(all.stream().map(SessionItem::getCompletedAt).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(Instant.now()));
        } else if (all.stream().anyMatch(i -> "IN_PROGRESS".equals(i.getStatus()))) s.setStatus("IN_PROGRESS");
        else if (all.stream().anyMatch(i -> "SCHEDULED".equals(i.getStatus()))) s.setStatus("SCHEDULED");
        else if (all.stream().anyMatch(i -> "COMPLETED".equals(i.getStatus()))) s.setStatus("AWAITING_RESULT");
        else s.setStatus("PLANNED");
    }

    private Plan validatedPlan(Plan p) {
        if (p == null || p.serviceId() == null || p.name() == null || p.name().isBlank() || p.name().length() > 150
                || p.price() == null || p.price().compareTo(BigDecimal.ONE) < 0 || p.price().scale() > 2
                || p.price().compareTo(new BigDecimal("99999999")) > 0 || p.sessions() == null || p.sessions().isEmpty() || p.sessions().size() > 100
                || p.terms() == null || p.terms().isBlank() || p.terms().length() > 10000)
            throw business("Treatment name, parent service, positive price, terms and 1-100 sessions are required");
        if (p.validityDays() != null && (p.validityDays() < 1 || p.validityDays() > 3650)) throw business("Validity must be 1-3650 days or unlimited");
        BeautyService parent = availableService(p.serviceId());
        if (!"TREATMENT_COURSE".equals(parent.getServiceType())) throw business("The parent service must have type TREATMENT_COURSE");
        List<PlanSession> sessions = new ArrayList<>(); int index = 1;
        for (PlanSession s : p.sessions()) {
            if (s == null || s.sequence() != index++ || s.title() == null || s.title().isBlank() || s.title().length() > 150
                    || s.items() == null || s.items().isEmpty() || s.items().size() > 20) throw business("Sessions must have contiguous order, title and services");
            int min = s.minGapDays() == null ? 0 : s.minGapDays();
            if (min < 0 || min > 3650 || (s.maxGapDays() != null && (s.maxGapDays() < min || s.maxGapDays() > 3650))) throw business("Invalid session interval");
            List<PlanItem> components = new ArrayList<>(); Set<Long> seen = new HashSet<>();
            for (PlanItem i : s.items()) {
                if (i == null || i.serviceId() == null || !seen.add(i.serviceId())) throw business("Session services must not be duplicated");
                BeautyService svc = availableService(i.serviceId());
                if ("TREATMENT_COURSE".equals(svc.getServiceType())) throw business("A session component must be a single-session service");
                components.add(new PlanItem(svc.getId(), svc.getName(), i.required() == null || i.required()));
            }
            if (components.stream().noneMatch(i -> Boolean.TRUE.equals(i.required()))) throw business("Each session must contain a required service");
            sessions.add(new PlanSession(s.sequence(), s.title().trim(), min, s.maxGapDays(), s.required() == null || s.required(), components));
        }
        return new Plan(p.name().trim(), optionalText(p.description(), 10000), p.serviceId(), p.price(), p.validityDays(), p.terms().trim(), sessions);
    }

    private TemplateView templateView(Template t, boolean admin) {
        Long versionId = admin && t.getDraftVersionId() != null ? t.getDraftVersionId() : t.getPublishedVersionId();
        TemplateVersion v = get(TemplateVersion.class, versionId); Plan p = read(v.getPlanJson());
        List<String> actions = admin ? (t.getDraftVersionId() == null ? List.of("CREATE_VERSION", "ARCHIVE") : List.of("EDIT", "PUBLISH", "ARCHIVE")) : (treatmentSalesEnabled ? List.of("PURCHASE") : List.of());
        return new TemplateView(t.getId(), v.getId(), v.getVersionNumber(), t.isArchived() ? "ARCHIVED" : v.getStatus(), p.name(), p.description(), p.serviceId(), v.getPackageId(), p.price(), p.validityDays(), p.terms(), p.sessions(), actions);
    }

    private CourseView courseView(Course c, boolean clinical) {
        Plan p = read(c.getPlanJson()); boolean owner = Objects.equals(c.getUserId(), SecurityUtils.getCurrentUserIdOptional().orElse(null));
        List<SessionView> sessionViews = sessions(c).stream().map(s -> sessionView(c, s, clinical)).toList();
        int completed = (int) sessionViews.stream().filter(s -> "COMPLETED".equals(s.status())).count();
        List<ChangeView> changeViews = list(Change.class, "select x from TreatmentChange x where x.courseId=:cid order by x.id desc", "cid", c.getId()).stream()
                .map(x -> new ChangeView(x.getId(), x.getStatus(), x.getBaseRevision(), x.getReason(), read(x.getPlanJson()), read(x.getOldPlanJson()), x.getPriceDifference(), x.getSettlementOrderId(), x.getProposedBy(), x.getApprovedBy(), x.getCreatedAt(), x.getAcceptedAt())).toList();
        List<RequestView> requestViews = list(WorkflowRequest.class, "select r from TreatmentWorkflowRequest r where r.courseId=:cid order by r.id desc", "cid", c.getId()).stream()
                .map(r -> new RequestView(r.getId(), r.getType(), r.getStatus(), r.getReason(), r.getExtensionDays(), r.getRefundAmount(), r.getRefundReference(), r.getReviewReason(), r.getCreatedAt())).toList();
        List<AuditView> audits = clinical ? list(Audit.class, "select a from TreatmentAudit a where a.courseId=:cid order by a.id desc", "cid", c.getId()).stream().map(a -> new AuditView(a.getId(), a.getAction(), a.getActorId(), a.getDetails(), a.getCreatedAt())).toList() : List.of();
        List<String> actions = new ArrayList<>(); String state = effectiveStatus(c);
        if (access.canManageReception() && !Set.of("COMPLETED", "CANCELLED", "REFUNDED").contains(state)) actions.add("ASSIGN");
        if (clinical && "PENDING_INTAKE".equals(state)) actions.add("INTAKE");
        if (clinical && Set.of("ACTIVE", "PENDING_INTAKE", "PAUSED").contains(state)) actions.add("PROPOSE_CHANGE");
        if (clinical && "ACTIVE".equals(state) && !hasLiveBookings(c) && sessionViews.stream().filter(SessionView::required).allMatch(s -> Set.of("COMPLETED", "WAIVED").contains(s.status()))) actions.add("COMPLETE");
        if ((owner || access.canManageReception()) && !Set.of("COMPLETED", "CANCELLED", "REFUNDED").contains(state)) {
            actions.addAll(List.of("REQUEST_CANCEL", "REQUEST_EXTEND", "REQUEST_INCIDENT"));
            if (Set.of("ACTIVE", "PENDING_INTAKE").contains(state)) actions.add("REQUEST_PAUSE");
            if (Set.of("PAUSED", "PENDING_REVIEW").contains(state)) actions.add("REQUEST_RESUME");
        } else if (clinical && !Set.of("COMPLETED", "CANCELLED", "REFUNDED").contains(state)) {
            actions.add("REQUEST_INCIDENT");
        }
        return new CourseView(c.getId(), c.getUserId(), c.getTemplateId(), c.getVersionId(), p.name(), state, c.getPlanRevision(), c.getOrderId(), c.getTicketId(), c.getAssignedStaffId(), c.getExpiryDate(), clinical ? c.getIntakeNotes() : null, clinical || owner ? c.getSummary() : null, c.getPaidAmount(), completed, sessionViews.size(), sessionViews.isEmpty() ? 0 : completed * 100 / sessionViews.size(), sessionViews, clinical || owner ? changeViews : List.of(), clinical || owner ? requestViews : List.of(), audits, actions, p, p);
    }

    private SessionView sessionView(Course c, Session s, boolean clinical) {
        boolean sharedResultVisible = clinical || (Objects.equals(c.getUserId(), SecurityUtils.getCurrentUserIdOptional().orElse(null)) && s.isSharedWithCustomer());
        List<ItemView> views = items(s).stream().map(i -> {
            AppointmentItem appointmentItem = i.getAppointmentItemId() == null ? null : em.find(AppointmentItem.class, i.getAppointmentItemId());
            Appointment a = appointmentItem == null ? null : appointmentItem.getAppointment();
            return new ItemView(i.getId(), i.getServiceId(), i.getServiceName(), c.getTicketId(), i.getStatus(), i.isRequired(), i.getAppointmentItemId(), a == null ? null : a.getId(), a == null ? null : a.getStatus().name(), a == null ? null : a.getAppointmentDate().toString(), appointmentItem == null || appointmentItem.getStartTime() == null ? null : appointmentItem.getStartTime().toString(), i.getAllocatedAmount(), sharedResultVisible ? s.getResult() : null, sharedResultVisible ? s.getAftercare() : null);
        }).toList();
        ItemView live = views.stream().filter(i -> i.appointmentId() != null && Set.of("SCHEDULED", "IN_PROGRESS").contains(i.status())).findFirst().orElse(null);
        String blocked = blocked(c, s); List<String> actions = new ArrayList<>();
        if (blocked == null && views.stream().anyMatch(i -> !"COMPLETED".equals(i.status()))) actions.add("BOOK");
        if (clinical && views.stream().anyMatch(i -> "COMPLETED".equals(i.status()))) actions.add("WRITE_RESULT");
        List<ResultView> results = list(SessionResult.class, "select r from TreatmentSessionResult r where r.sessionId=:sid order by r.id desc", "sid", s.getId()).stream()
                .filter(r -> clinical || (Objects.equals(c.getUserId(), SecurityUtils.getCurrentUserIdOptional().orElse(null)) && r.isSharedWithCustomer()))
                .map(r -> new ResultView(r.getId(), r.getAuthorId(), r.getResult(), r.getAftercare(), clinical ? r.getInternalNotes() : null, r.isSharedWithCustomer(), r.getCreatedAt())).toList();
        return new SessionView(s.getId(), s.getSequence(), s.getTitle(), s.getStatus(), s.getMinGapDays(), s.getMaxGapDays(), s.isRequired(), earliest(c, s), latest(c, s), blocked, live == null ? null : live.appointmentId(), live == null ? null : live.appointmentDate(), live == null ? null : live.startTime(), sharedResultVisible ? s.getResult() : null, sharedResultVisible ? s.getAftercare() : null, clinical ? s.getInternalNotes() : null, s.getCompletedAt(), views, actions, results);
    }

    private void createSessions(Course c, Plan plan) {
        int count = plan.sessions().stream().mapToInt(s -> s.items().size()).sum();
        BigDecimal per = plan.price().setScale(0, RoundingMode.HALF_UP).divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
        BigDecimal assigned = BigDecimal.ZERO; int index = 0;
        for (PlanSession ps : plan.sessions()) {
            Session s = new Session(); s.setCourseId(c.getId()); s.setSequence(ps.sequence()); s.setTitle(ps.title()); s.setMinGapDays(ps.minGapDays()); s.setMaxGapDays(ps.maxGapDays()); s.setRequired(Boolean.TRUE.equals(ps.required())); persist(s);
            for (PlanItem pi : ps.items()) {
                SessionItem item = new SessionItem(); item.setCourseId(c.getId()); item.setSessionId(s.getId()); item.setServiceId(pi.serviceId()); item.setServiceName(pi.serviceName()); item.setRequired(Boolean.TRUE.equals(pi.required()));
                BigDecimal amount = ++index == count ? plan.price().setScale(0, RoundingMode.HALF_UP).subtract(assigned) : per;
                item.setAllocatedAmount(amount); assigned = assigned.add(amount); persist(item, false);
            }
        }
        em.flush();
    }

    private void validateAmendment(Course c, Plan plan) {
        List<Session> current = sessions(c);
        BigDecimal earned = c.getConsumedAmount().add(current.stream().filter(s -> items(s).stream().anyMatch(i -> "COMPLETED".equals(i.getStatus())))
                .flatMap(s -> items(s).stream()).filter(i -> !financiallyConsumed(i)).map(SessionItem::getAllocatedAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        if (plan.price().compareTo(earned) < 0) throw business("A changed plan cannot refund already performed treatment");
        for (Session s : current) {
            List<SessionItem> existing = items(s);
            boolean performed = existing.stream().anyMatch(i -> "COMPLETED".equals(i.getStatus()));
            if (performed) {
                if (plan.sessions().size() < s.getSequence()) throw business("Performed sessions cannot be removed");
                PlanSession replacement = plan.sessions().get(s.getSequence() - 1);
                List<Long> a = existing.stream().map(SessionItem::getServiceId).toList();
                List<Long> b = replacement.items().stream().map(PlanItem::serviceId).toList();
                if (!a.equals(b) || replacement.minGapDays() != s.getMinGapDays() || !Objects.equals(replacement.maxGapDays(), s.getMaxGapDays())
                        || Boolean.TRUE.equals(replacement.required()) != s.isRequired()
                        || !existing.stream().map(SessionItem::isRequired).toList().equals(replacement.items().stream().map(i -> Boolean.TRUE.equals(i.required())).toList())) throw business("Performed session commitments cannot change");
            }
        }
    }

    private void applyChange(Course c, Change change) {
        requireOpen(c);
        if (change.getBaseRevision() != c.getPlanRevision()) throw business("The plan changed; submit a new revision");
        Plan replacement = read(change.getPlanJson()); validateAmendment(c, replacement);
        UserServiceTicket ticket = tickets.findByIdForUpdate(c.getTicketId()).orElseThrow(() -> missing("Ticket"));
        if (ticket.getReservedSessions() > 0) throw business("Release booked sessions before applying a revision");
        Map<Integer, Session> bySequence = sessions(c).stream().collect(Collectors.toMap(Session::getSequence, Function.identity()));
        for (Session old : new ArrayList<>(bySequence.values())) {
            if (items(old).stream().noneMatch(i -> "COMPLETED".equals(i.getStatus()))) {
                items(old).forEach(i -> i.setRetired(true)); old.setRetired(true); bySequence.remove(old.getSequence());
            }
        }
        em.flush();
        Map<Long, Integer> counts = counts(replacement); int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        for (Map.Entry<Long, TicketEntitlement> e : ticket.getEntitlements().entrySet()) {
            if (e.getValue().getUsed() > counts.getOrDefault(e.getKey(), 0)) throw business("A revision cannot remove already consumed rights");
        }
        ticket.getEntitlements().keySet().removeIf(id -> !counts.containsKey(id));
        counts.forEach((serviceId, n) -> {
            TicketEntitlement entitlement = ticket.getEntitlements().computeIfAbsent(serviceId, k -> new TicketEntitlement(0, 0)); entitlement.setTotal(n);
        }); ticket.setTotalSessions(total);
        if (ticket.getStatus() == TicketStatus.COMPLETED && ticket.getUsedSessions() < total) ticket.setStatus(TicketStatus.ACTIVE);
        BigDecimal preservedOutstanding = bySequence.values().stream().flatMap(s -> items(s).stream()).filter(i -> !financiallyConsumed(i)).map(SessionItem::getAllocatedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remainingValue = replacement.price().setScale(0, RoundingMode.HALF_UP).subtract(c.getConsumedAmount()).subtract(preservedOutstanding);
        int newCount = replacement.sessions().stream().filter(s -> !bySequence.containsKey(s.sequence())).mapToInt(s -> s.items().size()).sum();
        BigDecimal per = newCount == 0 ? BigDecimal.ZERO : remainingValue.max(BigDecimal.ZERO).divide(BigDecimal.valueOf(newCount), 2, RoundingMode.DOWN);
        int newIndex = 0; BigDecimal allocated = BigDecimal.ZERO;
        for (PlanSession ps : replacement.sessions()) if (!bySequence.containsKey(ps.sequence())) {
            Session s = new Session(); s.setCourseId(c.getId()); s.setPlanRevision(c.getPlanRevision() + 1); s.setSequence(ps.sequence()); s.setTitle(ps.title()); s.setMinGapDays(ps.minGapDays()); s.setMaxGapDays(ps.maxGapDays()); s.setRequired(Boolean.TRUE.equals(ps.required())); persist(s);
            for (PlanItem pi : ps.items()) {
                SessionItem i = new SessionItem(); i.setCourseId(c.getId()); i.setSessionId(s.getId()); i.setServiceId(pi.serviceId()); i.setServiceName(pi.serviceName()); i.setRequired(Boolean.TRUE.equals(pi.required()));
                BigDecimal amount = ++newIndex == newCount ? remainingValue.max(BigDecimal.ZERO).subtract(allocated) : per; i.setAllocatedAmount(amount); allocated = allocated.add(amount); persist(i, false);
            }
        }
        if (!Objects.equals(replacement.validityDays(), read(c.getPlanJson()).validityDays())) {
            Instant paidAt = orders.findPaidAtForUser(c.getOrderId(), c.getUserId()).orElseThrow(() -> business("Missing original paid date"));
            c.setExpiryDate(replacement.validityDays() == null ? null : paidAt.plus(replacement.validityDays(), ChronoUnit.DAYS)); ticket.setExpiryDate(c.getExpiryDate());
        }
        if (change.getPriceDifference().signum() > 0) c.setPaidAmount(c.getPaidAmount().add(change.getPriceDifference()));
        if (change.getPriceDifference().signum() < 0) {
            WorkflowRequest refund = new WorkflowRequest(); refund.setCourseId(c.getId()); refund.setType("PRICE_ADJUSTMENT"); refund.setReason(change.getReason()); refund.setRequestedBy(change.getProposedBy());
            refund.setReviewedBy(change.getApprovedBy()); refund.setReviewedAt(Instant.now()); refund.setStatus("AWAITING_REFUND"); refund.setRefundAmount(change.getPriceDifference().negate()); persist(refund);
        }
        c.setPlanJson(change.getPlanJson()); c.setPlanRevision(c.getPlanRevision() + 1); change.setStatus("APPLIED"); change.setAppliedAt(Instant.now());
        audit(c, "CHANGE_APPLIED", "Accepted plan revision " + c.getPlanRevision()); em.flush();
    }

    static boolean appliedRevisionMatches(Course course, Change change) {
        return "APPLIED".equals(change.getStatus()) && course.getPlanRevision() == change.getBaseRevision() + 1
                && Objects.equals(course.getPlanJson(), change.getPlanJson()) && change.getAcceptedAt() != null
                && change.getApprovedBy() != null && Objects.equals(course.getUserId(), change.getAcceptedBy());
    }

    private void queueUnappliedSettlementRefund(Course course, Change change) {
        if (!"AWAITING_SETTLEMENT".equals(change.getStatus())) return;
        WorkflowRequest refund = new WorkflowRequest(); refund.setCourseId(course.getId()); refund.setType("UNAPPLIED_SETTLEMENT");
        refund.setReason("Paid adjustment was not applied before the treatment closed"); refund.setRequestedBy(course.getUserId());
        refund.setReviewedBy(SecurityUtils.getCurrentUserIdOptional().orElse(null)); refund.setReviewedAt(Instant.now());
        refund.setRefundAmount(change.getPriceDifference()); refund.setStatus("AWAITING_REFUND"); persist(refund);
        change.setStatus("SETTLEMENT_REFUND_PENDING"); audit(course, "SETTLEMENT_REFUND_PENDING", "Unapplied paid adjustment requires refund");
    }

    private void cancelFutureBookings(Course c) {
        cancelFutureBookings(c, false);
    }

    private void cancelFutureBookings(Course c, boolean leaveActiveExecution) {
        List<Long> appointmentIds = list(SessionItem.class, "select i from TreatmentSessionItem i where i.courseId=:cid and i.appointmentItemId is not null", "cid", c.getId()).stream()
                .map(i -> em.find(AppointmentItem.class, i.getAppointmentItemId())).filter(Objects::nonNull).map(AppointmentItem::getAppointment).filter(Objects::nonNull)
                .filter(a -> (leaveActiveExecution ? Set.of("PENDING", "CONFIRMED") : Set.of("PENDING", "CONFIRMED", "IN_PROGRESS")).contains(a.getStatus().name())).map(Appointment::getId).distinct().sorted().toList();
        for (Long appointmentId : appointmentIds) {
            Appointment appointment = em.find(Appointment.class, appointmentId);
            if (appointment.getItems().stream().anyMatch(i -> "IN_PROGRESS".equals(i.getExecutionStatus().name()))) throw business("Finish or stop the current execution before changing the course state");
            appointments.cancelAppointment(appointmentId, "Treatment workflow: pause/cancel");
        }
    }

    boolean hasLiveBookings(Course c) {
        return !list(SessionItem.class, "select i from TreatmentSessionItem i where i.courseId=:cid and i.retired=false and "
                + "(i.status in ('SCHEDULED','IN_PROGRESS') or i.appointmentItemId in "
                + "(select ai.id from AppointmentItem ai where ai.isDeleted=false and ai.appointment.isDeleted=false and ai.appointment.status in "
                + "(com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus.PENDING, "
                + "com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus.CONFIRMED, "
                + "com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus.IN_PROGRESS)))", "cid", c.getId()).isEmpty();
    }
    private BigDecimal refundable(Course c) {
        if ("REFUNDED".equals(c.getStatus())) return BigDecimal.ZERO;
        return read(c.getPlanJson()).price().setScale(0, RoundingMode.HALF_UP).subtract(c.getConsumedAmount()).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    void recordConsumption(Course c, SessionItem item, AppointmentItem appointmentItem) {
        if (appointmentItem.getId() == null || financiallyConsumed(item)) return;
        boolean performed = "PERFORMED".equals(appointmentItem.getExecutionStatus().name());
        boolean forfeited = "NO_SHOW".equals(appointmentItem.getExecutionStatus().name()) && "FORFEITED".equals(appointmentItem.getTicketUsageState().name());
        if (!performed && !forfeited) return;
        FinancialConsumption evidence = new FinancialConsumption(); evidence.setCourseId(c.getId()); evidence.setAppointmentItemId(appointmentItem.getId());
        evidence.setSessionItemId(item.getId()); evidence.setAmount(item.getAllocatedAmount()); evidence.setKind(forfeited ? "FORFEIT" : "PERFORMED"); persist(evidence, false);
        c.setConsumedAmount(c.getConsumedAmount().add(item.getAllocatedAmount()));
        if (performed && loyalty!=null) loyalty.performedCourse(c);
    }
    private boolean financiallyConsumed(SessionItem item) { return first(FinancialConsumption.class, "select f from TreatmentFinancialConsumption f where f.sessionItemId=:sid", "sid", item.getId()) != null; }
    private ServicePackage createManagedPackage(Plan p, BigDecimal price) {
        ServicePackage pack = new ServicePackage(); pack.setName(p.name()); pack.setDescription(p.description()); pack.setPrice(price);
        pack.setValidityDays(p.validityDays()); pack.setIsActive(false); pack.setTreatmentManaged(true); pack.setItems(new ArrayList<>());
        counts(p).forEach((id, n) -> pack.getItems().add(ServicePackageItem.builder().servicePackage(pack).service(services.getReferenceById(id)).quantity(n).build()));
        return packages.saveAndFlush(pack);
    }
    private SpaPackageOrderResult orderFor(Long uid, Long packageId, Plan p, String notes, String key) {
        var user = identity.getUserSummaryById(uid);
        return orders.createSpaPackageOrder(CreateSpaPackageOrderCommand.builder().userId(uid).servicePackageId(packageId).benefitType("TREATMENT_COURSE").packageName(p.name()).amount(p.price())
                .customerName(user.getFullName()).customerPhone(user.getPhone()).notes(optionalText(notes, 500)).idempotencyKey(key).build());
    }
    private void savePurchaseSnapshot(Long orderId, Long packageId, Plan plan, BigDecimal purchaseAmount) {
        if (snapshots.existsById(orderId)) return;
        SpaPurchaseSnapshot snapshot = new SpaPurchaseSnapshot(); snapshot.setOrderId(orderId); snapshot.setPackageId(packageId); snapshot.setValidityDays(plan.validityDays()); snapshot.setExpiryCheckMode(policies.expiryCheckMode()); snapshot.getEntitlements().putAll(counts(plan));
        snapshot.setPackageName(em.getReference(ServicePackage.class, packageId).getName());
        snapshot.setPurchaseAmount(purchaseAmount);
        counts(plan).keySet().forEach(id -> { var service = em.find(BeautyService.class, id); snapshot.getServiceDetails().put(id, new PurchasedServiceDetail(service.getName(), service.getBasePrice())); });
        snapshots.save(snapshot);
    }
    private Map<Long, Integer> counts(Plan p) { Map<Long, Integer> result = new TreeMap<>(); p.sessions().forEach(s -> s.items().forEach(i -> result.merge(i.serviceId(), 1, Math::addExact))); return result; }
    private Instant earliest(Course c, Session s) {
        Session previous = sessions(c).stream().filter(x -> x.getSequence() < s.getSequence() && x.getCompletedAt() != null)
                .max(Comparator.comparingInt(Session::getSequence)).orElse(null);
        return previous == null || previous.getCompletedAt() == null ? null : previous.getCompletedAt().plus(s.getMinGapDays(), ChronoUnit.DAYS);
    }
    private Instant latest(Course c, Session s) {
        Session previous = sessions(c).stream().filter(x -> x.getSequence() < s.getSequence() && x.getCompletedAt() != null)
                .max(Comparator.comparingInt(Session::getSequence)).orElse(null);
        return previous == null || previous.getCompletedAt() == null || s.getMaxGapDays() == null ? null : previous.getCompletedAt().plus(s.getMaxGapDays(), ChronoUnit.DAYS);
    }
    private boolean expired(Course c) { return c.getExpiryDate() != null && !c.getExpiryDate().isAfter(Instant.now()); }
    private String effectiveStatus(Course c) { return expired(c) && !Set.of("COMPLETED", "CANCELLED", "REFUNDED").contains(c.getStatus()) ? "EXPIRED" : c.getStatus(); }
    private Session session(Course c, Long id) { Session s = get(Session.class, id); if (!Objects.equals(s.getCourseId(), c.getId())) throw missing("Session"); return s; }
    private Change change(Course c, Long id) { Change x = get(Change.class, id); if (!Objects.equals(x.getCourseId(), c.getId())) throw missing("Change"); return x; }
    private WorkflowRequest workflow(Course c, Long id) { WorkflowRequest r = get(WorkflowRequest.class, id); if (!Objects.equals(r.getCourseId(), c.getId())) throw missing("Request"); return r; }
    private BeautyService availableService(Long id) { return services.findById(id).filter(s -> !Boolean.TRUE.equals(s.getIsDeleted()) && Boolean.TRUE.equals(s.getIsActive())).orElseThrow(() -> business("Service is unavailable: " + id)); }
    private boolean isAssigned(Course c) { return c.getAssignedStaffId() != null && staff.findById(c.getAssignedStaffId()).filter(s -> !Boolean.TRUE.equals(s.getIsDeleted()) && Boolean.TRUE.equals(s.getIsActive()) && Objects.equals(s.getUserId(), SecurityUtils.getCurrentUserIdOptional().orElse(null))).isPresent(); }
    private void requireView(Course c) { if (!Objects.equals(c.getUserId(), SecurityUtils.getCurrentUserId()) && !access.canManageReception() && !(access.permission("SPA_EXECUTION") && isAssigned(c))) throw denied(); }
    private void requireOwner(Course c) { if (!Objects.equals(c.getUserId(), SecurityUtils.getCurrentUserId())) throw new AccessDeniedException("Only the customer may confirm their treatment plan"); }
    private void requireClinical(Course c) { if (!access.hasRole("ADMIN") && !(access.permission("SPA_EXECUTION") && isAssigned(c))) throw new AccessDeniedException("Clinical changes require the assigned staff member"); }
    private void requireOperator() { if (!access.canManageReception() && !access.permission("SPA_EXECUTION")) throw denied(); }
    private void requireAdmin() { if (!access.hasRole("ADMIN")) throw denied(); }
    private void requireOpen(Course c) { if (!Set.of("PENDING_INTAKE", "ACTIVE", "PAUSED", "PENDING_REVIEW").contains(effectiveStatus(c))) throw business("This treatment is not open for changes"); }
    private void expected(Course c, Integer version) { if (version == null || version != c.getPlanRevision()) throw business("The treatment plan changed; reload before continuing"); }
    void audit(Course c, String action, String details) { Audit a = new Audit(); a.setCourseId(c.getId()); a.setActorId(SecurityUtils.getCurrentUserIdOptional().orElse(null)); a.setAction(action); a.setDetails(details); persist(a, false); notifications.record(c.getId(), c.getUserId(), action, a.getId()); }
    private String reason(DecisionCommand c) { return c == null ? null : c.reason(); }
    private String reasonOr(String reason, String fallback) { return reason == null || reason.isBlank() ? fallback : optionalText(reason, 1000); }
    private String text(String value, String label, int max) { if (value == null || value.isBlank()) throw business(label + " is required"); return optionalText(value, max); }
    private String optionalText(String value, int max) { if (value != null && value.length() > max) throw business("Text exceeds " + max + " characters"); return value == null ? null : value.trim(); }
    private String purchaseKey(Long uid, String key) { return hash("treatment:" + uid + ":" + key); }
    private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String write(Plan p) { return writeObject(p); }
    private String writeObject(Object p) { try { return json.writeValueAsString(p); } catch (Exception e) { throw new IllegalStateException("Cannot snapshot treatment", e); } }
    private Plan read(String value) { try { return json.readValue(value, Plan.class); } catch (Exception e) { throw new IllegalStateException("Invalid treatment snapshot", e); } }
    private <T> T get(Class<T> type, Long id) { T value = id == null ? null : em.find(type, id); if (value == null) throw missing(type.getSimpleName()); return value; }
    private <T> T lock(Class<T> type, Long id) { T value = id == null ? null : em.find(type, id, LockModeType.PESSIMISTIC_WRITE); if (value == null) throw missing(type.getSimpleName()); return value; }
    private void persist(Object entity) { persist(entity, true); }
    private void persist(Object entity, boolean flush) { em.persist(entity); if (flush) em.flush(); }
    private <T> List<T> list(Class<T> type, String jpql, Object... params) { var query = em.createQuery(jpql, type); for (int i = 0; i < params.length; i += 2) query.setParameter((String) params[i], params[i + 1]); return query.getResultList(); }
    private <T> T first(Class<T> type, String jpql, Object... params) { List<T> values = list(type, jpql, params); return values.isEmpty() ? null : values.get(0); }
    private BusinessException business(String message) { return new BusinessException(message); }
    private ResourceNotFoundException missing(String resource) { return new ResourceNotFoundException(resource + " was not found"); }
    private AccessDeniedException denied() { return new AccessDeniedException("You cannot access this treatment operation"); }
}
