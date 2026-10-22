package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.spa.domain.Appointment;
import com.core.beautyshop.modules.spa.domain.AppointmentItem;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class SpaAccessService {
    @org.springframework.beans.factory.annotation.Autowired
    private com.core.beautyshop.modules.identity.api.StaffPermissionService staffPermissions;

    public boolean permission(String code) { return hasRole("ADMIN") || (staffPermissions != null && staffPermissions.has(code)); }
    public boolean hasRole(String role) {
        if ("SPA_RECEPTION".equals(role)) return permission("SPA_RECEPTION");
        if ("SPA_THERAPIST".equals(role)) return permission("SPA_EXECUTION");
        if ("CS_STAFF".equals(role)) return permission("CRM");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && auth.getAuthorities().stream()
                .anyMatch(authority -> ("ROLE_" + role).equals(authority.getAuthority()));
    }

    public boolean canManageReception() {
        return permission("SPA_RECEPTION");
    }

    public boolean isAssignedTechnician(Appointment appointment) {
        Long userId = SecurityUtils.getCurrentUserIdOptional().orElse(null);
        return userId != null && appointment != null && !Boolean.TRUE.equals(appointment.getIsDeleted())
                && appointment.getItems() != null && appointment.getItems().stream()
                .anyMatch(item -> assignedTo(item, userId));
    }

    public void requireCanPerformItem(AppointmentItem item) {
        if (item == null || Boolean.TRUE.equals(item.getIsDeleted()) || item.getAppointment() == null
                || Boolean.TRUE.equals(item.getAppointment().getIsDeleted())) {
            throw new AccessDeniedException("Appointment item is unavailable");
        }
        if (item.getTreatmentSessionItemId() != null) {
            if (hasRole("ADMIN") || (permission("SPA_EXECUTION") && assignedTo(item, SecurityUtils.getCurrentUserId()))) return;
            throw new AccessDeniedException("Only the assigned staff member may perform treatment services");
        }
        if (hasRole("ADMIN") || (permission("SPA_EXECUTION") && assignedTo(item, SecurityUtils.getCurrentUserId()))) return;
        throw new AccessDeniedException("Only administrators and staff may perform this item");
    }

    public void requireCanViewAppointment(Appointment appointment) {
        if (appointment != null && !Boolean.TRUE.equals(appointment.getIsDeleted())
                && (Objects.equals(appointment.getUserId(), SecurityUtils.getCurrentUserId())
                || canManageReception() || permission("CRM")
                || (hasRole("SPA_THERAPIST") && isAssignedTechnician(appointment)))) return;
        throw new AccessDeniedException("You cannot view this appointment");
    }

    public void requireCanViewCareSummary(Appointment appointment) {
        if (isTreatmentAppointment(appointment)) {
            if (hasRole("ADMIN") || (permission("SPA_EXECUTION") && isAssignedTechnician(appointment))) return;
            throw new AccessDeniedException("Treatment care notes require an assigned staff member");
        }
        if (hasRole("ADMIN") || permission("CRM")
                || (hasRole("SPA_THERAPIST") && isAssignedTechnician(appointment))) return;
        throw new AccessDeniedException("Care summaries are restricted to administrators and staff");
    }

    public void requireCanViewPreparation(Appointment appointment) {
        requireCanViewAppointment(appointment);
        if (isTreatmentAppointment(appointment) && !hasRole("ADMIN")
                && !Objects.equals(appointment.getUserId(), SecurityUtils.getCurrentUserId())
                && !(permission("SPA_EXECUTION") && isAssignedTechnician(appointment)))
            throw new AccessDeniedException("Treatment forms require the customer or an assigned staff member");
    }

    public void requireCanViewFollowUp(Appointment appointment) {
        requireCanViewPreparation(appointment);
    }

    private boolean isTreatmentAppointment(Appointment appointment) {
        return appointment != null && appointment.getItems() != null
                && appointment.getItems().stream().anyMatch(item -> item.getTreatmentSessionItemId() != null);
    }

    private boolean assignedTo(AppointmentItem item, Long userId) {
        return !Boolean.TRUE.equals(item.getIsDeleted()) && item.getStaff() != null
                && !Boolean.TRUE.equals(item.getStaff().getIsDeleted())
                && Objects.equals(item.getStaff().getUserId(), userId);
    }
}
