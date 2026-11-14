package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.spa.domain.*;
import com.core.beautyshop.modules.spa.domain.treatment.TreatmentEntities.*;
import com.core.beautyshop.modules.spa.application.dto.request.AppointmentItemRequest;
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
            if (svc != null && "TREATMENT_COURSE".equals(svc.getServiceType())) throw business("Vui lòng mua gói liệu trình trước khi đặt lịch cho các buổi điều trị.");
            Course course = ticketId == null ? null : courses.courseForTicket(ticketId);
            if (course != null) { courses.lockCourse(course.getId()); throw business("Quyền lợi liệu trình cần được đặt lịch thông qua buổi điều trị tương ứng."); }
            return;
        }
        SessionItem observed = courses.item(sessionItemId); Course course = courses.lockCourse(observed.getCourseId());
        SessionItem item = courses.lockSessionItem(sessionItemId);
        if (!Objects.equals(course.getUserId(), userId) || !Objects.equals(item.getServiceId(), serviceId) || !Objects.equals(course.getTicketId(), ticketId))
            throw business("Buổi điều trị, khách hàng, dịch vụ và vé thanh toán không trùng khớp.");
        if (item.isRetired() || !"PLANNED".equals(item.getStatus())) throw business("Dịch vụ điều trị này đã ngừng áp dụng, đã có lịch hẹn hoặc đã hoàn thành.");
        Session session = courses.getSession(item.getSessionId()); String blocked = courses.blocked(course, session);
        if (blocked != null) throw business(blocked);
        if (scheduledStart == null || !courses.withinWindow(course, session, scheduledStart)) throw business("Lịch hẹn nằm ngoài khoảng thời gian hoặc thời hạn cho phép của buổi điều trị.");
    }

    public void linkBooking(Long sessionItemId, Long appointmentItemId) {
        if (sessionItemId == null) return;
        SessionItem observed = courses.item(sessionItemId); Course course = courses.lockCourse(observed.getCourseId());
        SessionItem item = courses.lockSessionItem(sessionItemId);
        if (!"PLANNED".equals(item.getStatus())) throw business("Dịch vụ liệu trình này đã có một lịch hẹn đang hoạt động.");
        AppointmentItem booking = em.find(AppointmentItem.class, appointmentItemId);
        if (booking == null || !Objects.equals(booking.getTreatmentSessionItemId(), sessionItemId)
                || !Objects.equals(booking.getAppointment().getUserId(), course.getUserId())) throw business("Liên kết lịch hẹn liệu trình không hợp lệ.");
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
            throw business("Hồ sơ liệu trình chưa được Bác sĩ tiếp nhận (Intake) hoặc đã hết hạn điều trị.");
        if (!Objects.equals(course.getUserId(), item.getAppointment().getUserId()) || item.getTicket() == null || !Objects.equals(course.getTicketId(), item.getTicket().getId())) throw business("Quyền sở hữu lộ trình hoặc vé liệu trình không khớp.");
        if (!access.hasRole("ADMIN") && (!access.hasRole("STAFF") || item.getStaff() == null
                || !Objects.equals(item.getStaff().getUserId(), SecurityUtils.getCurrentUserId())))
            throw new AccessDeniedException("Chỉ chuyên viên/bác sĩ được phân công mới có thể thực hiện dịch vụ liệu trình này.");
        Session session = courses.getSession(treatmentItem.getSessionId());
        if (courses.sessions(course).stream().anyMatch(s -> s.isRequired() && s.getSequence() < session.getSequence() && !Set.of("COMPLETED", "WAIVED", "NO_SHOW").contains(s.getStatus())))
            throw business("Cần hoàn tất buổi điều trị trước đó trước khi thực hiện buổi này.");
        if (!courses.withinWindow(course, session, Instant.now())) throw business("Thời điểm thực hiện nằm ngoài khoảng thời gian quy định của buổi điều trị.");
        if ("COMPLETED".equals(treatmentItem.getStatus())) throw business("Dịch vụ liệu trình này đã được hoàn thành trước đó.");
    }

    public void validateAppointmentStart(Appointment appointment) {
        if (appointment == null || appointment.getItems() == null) return;
        for (AppointmentItem item : appointment.getItems()) {
            if (item.getTreatmentSessionItemId() == null || Set.of("PERFORMED", "SKIPPED", "NO_SHOW").contains(item.getExecutionStatus().name())) continue;
            if (!treatmentExecutionEnabled) throw business("Thực hiện lộ trình đang tạm dừng theo cấu hình hệ thống");
            SessionItem observed = courses.item(item.getTreatmentSessionItemId()); Course course = courses.lockCourse(observed.getCourseId());
            SessionItem linked = courses.lockSessionItem(item.getTreatmentSessionItemId()); requireLink(linked, item);
            if (!"ACTIVE".equals(course.getStatus()))
                throw business("Hồ sơ liệu trình chưa được Bác sĩ tiếp nhận (Intake). Vui lòng hoàn tất khám sàng lọc trước khi bắt đầu dịch vụ.");
            if (!courses.withinWindow(course, courses.getSession(linked.getSessionId()), Instant.now()))
                throw business("Thời điểm thực hiện nằm ngoài khoảng thời gian quy định hoặc thời hạn của lộ trình.");
            Session session = courses.getSession(linked.getSessionId());
            if (courses.sessions(course).stream().anyMatch(s -> s.isRequired() && s.getSequence() < session.getSequence() && !Set.of("COMPLETED", "WAIVED", "NO_SHOW").contains(s.getStatus()))) throw business("Cần hoàn tất buổi điều trị trước đó trước khi bắt đầu buổi này.");
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
        else if ("NO_SHOW".equals(state) && item.getTicketUsageState() != null && "FORFEITED".equals(item.getTicketUsageState().name())) {
            treatmentItem.setStatus("NO_SHOW"); treatmentItem.setCompletedAt(item.getActualCompletedAt() == null ? Instant.now() : item.getActualCompletedAt());
        } else if (Set.of("SKIPPED", "NO_SHOW").contains(state) || (item.getAppointment() != null && "CANCELLED".equals(item.getAppointment().getStatus().name()))) {
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
        if (!Set.of("COMPLETED", "NO_SHOW").contains(treatmentItem.getStatus())) { treatmentItem.setStatus("PLANNED"); treatmentItem.setAppointmentItemId(null); }
        courses.refreshSession(courses.getSession(treatmentItem.getSessionId()));
    }

    public void validateReschedule(AppointmentItem item, Instant newStart) {
        if (item == null || item.getTreatmentSessionItemId() == null) return;
        SessionItem observed = courses.item(item.getTreatmentSessionItemId()); Course course = courses.lockCourse(observed.getCourseId());
        SessionItem treatmentItem = courses.lockSessionItem(item.getTreatmentSessionItemId()); requireLink(treatmentItem, item);
        if (!Set.of("PENDING_INTAKE", "ACTIVE").contains(course.getStatus()) || !courses.withinWindow(course, courses.getSession(treatmentItem.getSessionId()), newStart))
            throw business("Thời gian hẹn mới nằm ngoài khoảng thời gian cho phép hoặc thời hạn của lộ trình.");
    }

    public boolean canStaffBookForCustomer(Long staffUserId, Long targetUserId, List<AppointmentItemRequest> items) {
        if (staffUserId == null || targetUserId == null || items == null || items.isEmpty()) return false;
        if (!access.permission("SPA_EXECUTION")) return false;
        for (var item : items) {
            if (item.getTreatmentSessionItemId() == null) return false;
            SessionItem sessionItem = courses.item(item.getTreatmentSessionItemId());
            if (sessionItem == null) return false;
            Course course = courses.getCourse(sessionItem.getCourseId());
            if (course == null || !Objects.equals(course.getUserId(), targetUserId) || !courses.isAssigned(course)) return false;
        }
        return true;
    }

    private void requireLink(SessionItem treatmentItem, AppointmentItem item) {
        if (!Objects.equals(treatmentItem.getAppointmentItemId(), item.getId())) throw business("Lịch hẹn này không còn giữ chỗ cho buổi điều trị.");
    }
    private BusinessException business(String message) { return new BusinessException(message); }
}
