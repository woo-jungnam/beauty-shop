package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.spa.domain.*;
import com.core.beautyshop.modules.spa.domain.treatment.TreatmentEntities.*;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional
public class TreatmentAppointmentBridge {
    private final TreatmentCourseService courses;
    private final BeautyServiceRepository services;
    private final SpaAccessService access;
    @PersistenceContext private EntityManager em;
    @org.springframework.beans.factory.annotation.Value("${app.spa.treatment.execution-enabled:true}")
    private boolean treatmentExecutionEnabled = true;

    public void lockForAppointment(Long appointmentId) {
        List<Long> ids = em.createQuery("select distinct i.courseId from TreatmentSessionItem i where i.appointmentItemId in (select ai.id from AppointmentItem ai where ai.appointment.id=:aid) order by i.courseId", Long.class)
                .setParameter("aid", appointmentId).getResultList();
        ids.forEach(courses::lockCourse);
    }

    public void lockAppointmentCourse(Appointment appointment) {
        if (appointment != null && appointment.getItems() != null) appointment.getItems().stream()
                .map(AppointmentItem::getTreatmentSessionItemId).filter(Objects::nonNull)
                .map(courses::item).map(SessionItem::getCourseId).distinct().sorted().forEach(courses::lockCourse);
    }

    public void validateAndLockBooking(Long sessionItemId, Long userId, Long serviceId, Long ticketId, Instant scheduledStart) {
        if (sessionItemId == null) {
            BeautyService svc = serviceId == null ? null : services.findById(serviceId).orElse(null);
            if (svc != null && "TREATMENT_COURSE".equals(svc.getServiceType())) throw business("Purchase a treatment plan before booking its sessions");
            Course course = ticketId == null ? null : courses.courseForTicket(ticketId);
            if (course != null) { courses.lockCourse(course.getId()); throw business("Treatment rights must be booked through their treatment session"); }
            return;
        }
        SessionItem observed = courses.item(sessionItemId); Course course = courses.lockCourse(observed.getCourseId());
        SessionItem item = courses.lockSessionItem(sessionItemId);
        if (!Objects.equals(course.getUserId(), userId) || !Objects.equals(item.getServiceId(), serviceId) || !Objects.equals(course.getTicketId(), ticketId))
            throw business("Treatment session, customer, service and paid ticket must match");
        if (item.isRetired() || !"PLANNED".equals(item.getStatus())) throw business("This treatment service is retired, already booked or has been performed");
        Session session = courses.getSession(item.getSessionId()); String blocked = courses.blocked(course, session);
        if (blocked != null) throw business(blocked);
        if (scheduledStart == null || !courses.withinWindow(course, session, scheduledStart)) throw business("Appointment is outside the treatment session's allowed interval or validity");
    }

    public void linkBooking(Long sessionItemId, Long appointmentItemId) {
        if (sessionItemId == null) return;
        SessionItem observed = courses.item(sessionItemId); Course course = courses.lockCourse(observed.getCourseId());
        SessionItem item = courses.lockSessionItem(sessionItemId);
        if (!"PLANNED".equals(item.getStatus())) throw business("This treatment service already has a live booking");
        AppointmentItem booking = em.find(AppointmentItem.class, appointmentItemId);
        if (booking == null || !Objects.equals(booking.getTreatmentSessionItemId(), sessionItemId)
                || !Objects.equals(booking.getAppointment().getUserId(), course.getUserId())) throw business("Invalid treatment appointment linkage");
        item.setAppointmentItemId(appointmentItemId); item.setStatus("SCHEDULED");
        courses.refreshSession(courses.getSession(item.getSessionId())); courses.audit(course, "SESSION_BOOKED", "Treatment session " + item.getSessionId() + "; appointment " + booking.getAppointment().getId());
    }

    public void validateExecution(AppointmentItem item) {
        if (item == null || item.getTreatmentSessionItemId() == null) return;
        if (!treatmentExecutionEnabled) throw business("Thực hiện lộ trình đang tạm dừng theo cấu hình hệ thống");
        SessionItem observed = courses.item(item.getTreatmentSessionItemId()); Course course = courses.lockCourse(observed.getCourseId());
        SessionItem treatmentItem = courses.lockSessionItem(item.getTreatmentSessionItemId());
        requireLink(treatmentItem, item);
        if (!"ACTIVE".equals(course.getStatus()) || (course.getExpiryDate() != null && !course.getExpiryDate().isAfter(Instant.now())))
            throw business("Intake must be accepted and treatment must be active and valid before execution");
        if (!Objects.equals(course.getUserId(), item.getAppointment().getUserId()) || item.getTicket() == null || !Objects.equals(course.getTicketId(), item.getTicket().getId())) throw business("Treatment ownership or ticket does not match");
        if (!access.hasRole("ADMIN") && (!access.hasRole("STAFF") || item.getStaff() == null
                || !Objects.equals(item.getStaff().getUserId(), SecurityUtils.getCurrentUserId())))
            throw new AccessDeniedException("Only the assigned staff member may perform this treatment service");
        Session session = courses.getSession(treatmentItem.getSessionId());
        if (courses.sessions(course).stream().anyMatch(s -> s.isRequired() && s.getSequence() < session.getSequence() && !Set.of("COMPLETED", "WAIVED").contains(s.getStatus())))
            throw business("Complete the previous treatment session first");
        if (!courses.withinWindow(course, session, Instant.now())) throw business("Execution is outside the treatment session's permitted interval");
        if ("COMPLETED".equals(treatmentItem.getStatus())) throw business("This treatment service was already performed");
    }

    public void validateAppointmentStart(Appointment appointment) {
        if (appointment == null || appointment.getItems() == null) return;
        for (AppointmentItem item : appointment.getItems()) {
            if (item.getTreatmentSessionItemId() == null || Set.of("PERFORMED", "SKIPPED", "NO_SHOW").contains(item.getExecutionStatus().name())) continue;
            if (!treatmentExecutionEnabled) throw business("Thực hiện lộ trình đang tạm dừng theo cấu hình hệ thống");
            SessionItem observed = courses.item(item.getTreatmentSessionItemId()); Course course = courses.lockCourse(observed.getCourseId());
            SessionItem linked = courses.lockSessionItem(item.getTreatmentSessionItemId()); requireLink(linked, item);
            if (!"ACTIVE".equals(course.getStatus()) || !courses.withinWindow(course, courses.getSession(linked.getSessionId()), Instant.now()))
                throw business("Intake must be accepted and treatment must be active within its permitted interval");
            Session session = courses.getSession(linked.getSessionId());
            if (courses.sessions(course).stream().anyMatch(s -> s.isRequired() && s.getSequence() < session.getSequence() && !Set.of("COMPLETED", "WAIVED").contains(s.getStatus()))) throw business("Complete the previous session first");
        }
    }

    public void synchronizeExecution(AppointmentItem item) {
        if (item == null || item.getTreatmentSessionItemId() == null) return;
        SessionItem observed = courses.item(item.getTreatmentSessionItemId()); Course course = courses.lockCourse(observed.getCourseId());
        SessionItem treatmentItem = courses.lockSessionItem(item.getTreatmentSessionItemId());
        requireLink(treatmentItem, item);
        String state = item.getExecutionStatus().name();
        courses.recordConsumption(course, treatmentItem, item);
        if ("PERFORMED".equals(state)) {
            treatmentItem.setStatus("COMPLETED"); treatmentItem.setCompletedAt(item.getActualCompletedAt() == null ? Instant.now() : item.getActualCompletedAt());
        } else if ("IN_PROGRESS".equals(state)) treatmentItem.setStatus("IN_PROGRESS");
        else if (Set.of("SKIPPED", "NO_SHOW").contains(state) || (item.getAppointment() != null && "CANCELLED".equals(item.getAppointment().getStatus().name()))) {
            treatmentItem.setStatus("PLANNED"); treatmentItem.setAppointmentItemId(null);
        }
        courses.refreshSession(courses.getSession(treatmentItem.getSessionId()));
        if ("PERFORMED".equals(state)) courses.audit(course, "SESSION_ITEM_PERFORMED", "Performed treatment service " + treatmentItem.getId());
    }

    public void releaseBooking(AppointmentItem item) {
        if (item == null || item.getTreatmentSessionItemId() == null) return;
        SessionItem observed = courses.item(item.getTreatmentSessionItemId()); Course course = courses.lockCourse(observed.getCourseId());
        SessionItem treatmentItem = courses.lockSessionItem(item.getTreatmentSessionItemId());
        if (!Objects.equals(treatmentItem.getAppointmentItemId(), item.getId())) return;
        courses.recordConsumption(course, treatmentItem, item);
        if (!"COMPLETED".equals(treatmentItem.getStatus())) { treatmentItem.setStatus("PLANNED"); treatmentItem.setAppointmentItemId(null); }
        courses.refreshSession(courses.getSession(treatmentItem.getSessionId()));
    }

    public void validateReschedule(AppointmentItem item, Instant newStart) {
        if (item == null || item.getTreatmentSessionItemId() == null) return;
        SessionItem observed = courses.item(item.getTreatmentSessionItemId()); Course course = courses.lockCourse(observed.getCourseId());
        SessionItem treatmentItem = courses.lockSessionItem(item.getTreatmentSessionItemId()); requireLink(treatmentItem, item);
        if (!Set.of("PENDING_INTAKE", "ACTIVE").contains(course.getStatus()) || !courses.withinWindow(course, courses.getSession(treatmentItem.getSessionId()), newStart))
            throw business("The new time is outside the active treatment plan's interval or validity");
    }

    private void requireLink(SessionItem treatmentItem, AppointmentItem item) {
        if (!Objects.equals(treatmentItem.getAppointmentItemId(), item.getId())) throw business("This appointment no longer owns the treatment session reservation");
    }
    private BusinessException business(String message) { return new BusinessException(message); }
}
