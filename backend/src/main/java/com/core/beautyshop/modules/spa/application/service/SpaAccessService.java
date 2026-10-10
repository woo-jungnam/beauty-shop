package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.spa.domain.Appointment;
import com.core.beautyshop.modules.spa.domain.AppointmentItem;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** Appointment access follows assigned staff user IDs, never client supplied staff IDs. */
@Service
public class SpaAccessService {
    public boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && auth.getAuthorities().stream()
                .anyMatch(authority -> ("ROLE_" + role).equals(authority.getAuthority()));
    }

    public boolean canManageReception() {
        return hasRole("ADMIN") || hasRole("STAFF") || hasRole("SPA_RECEPTION");
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
        if (hasRole("ADMIN") || hasRole("STAFF") || hasRole("SPA_RECEPTION")) return;
        if (hasRole("SPA_THERAPIST") && assignedTo(item, SecurityUtils.getCurrentUserId())) return;
        throw new AccessDeniedException("Only administrators and staff may perform this item");
    }

    public void requireCanViewAppointment(Appointment appointment) {
        if (appointment != null && !Boolean.TRUE.equals(appointment.getIsDeleted())
                && (Objects.equals(appointment.getUserId(), SecurityUtils.getCurrentUserId())
                || hasRole("ADMIN") || hasRole("STAFF") || hasRole("SPA_RECEPTION") || hasRole("CS_STAFF")
                || (hasRole("SPA_THERAPIST") && isAssignedTechnician(appointment)))) return;
        throw new AccessDeniedException("You cannot view this appointment");
    }

    public void requireCanViewCareSummary(Appointment appointment) {
        if (hasRole("ADMIN") || hasRole("STAFF") || hasRole("CS_STAFF")
                || (hasRole("SPA_THERAPIST") && isAssignedTechnician(appointment))) return;
        throw new AccessDeniedException("Care summaries are restricted to administrators and staff");
    }

    private boolean assignedTo(AppointmentItem item, Long userId) {
        return !Boolean.TRUE.equals(item.getIsDeleted()) && item.getStaff() != null
                && !Boolean.TRUE.equals(item.getStaff().getIsDeleted())
                && Objects.equals(item.getStaff().getUserId(), userId);
    }
}
