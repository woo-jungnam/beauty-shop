package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.spa.domain.*;
import com.core.beautyshop.shared.security.services.UserDetailsImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TreatmentAccessRulesTest {
    SpaAccessService access = new SpaAccessService();
    @org.junit.jupiter.api.BeforeEach void grants() {
        var permissions=org.mockito.Mockito.mock(com.core.beautyshop.modules.identity.api.StaffPermissionService.class);
        org.mockito.Mockito.when(permissions.has(org.mockito.ArgumentMatchers.anyString())).thenAnswer(call -> com.core.beautyshop.shared.security.utils.SecurityUtils.isStaffOrAdmin());
        org.springframework.test.util.ReflectionTestUtils.setField(access,"staffPermissions",permissions);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    void actor(Long id, String role) {
        var principal = new UserDetailsImpl(id, "test", "test@example.test", "unused", List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
    Appointment appointment() {
        var staff = Staff.builder().userId(10L).build();
        var a = Appointment.builder().userId(1L).build();
        var item = AppointmentItem.builder().appointment(a).staff(staff).treatmentSessionItemId(90L).build();
        a.setItems(List.of(item)); return a;
    }

    @Test void receptionCanCoordinateButCannotReadClinicalFormsOrPerformAnotherStaffsTreatment() {
        actor(20L, "ROLE_STAFF"); var a = appointment();
        assertDoesNotThrow(() -> access.requireCanViewAppointment(a));
        assertThrows(AccessDeniedException.class, () -> access.requireCanViewPreparation(a));
        assertThrows(AccessDeniedException.class, () -> access.requireCanViewCareSummary(a));
        assertThrows(AccessDeniedException.class, () -> access.requireCanPerformItem(a.getItems().getFirst()));
    }

    @Test void ownerMayReadTheirFormsButNotPrivateCareSummary() {
        actor(1L, "ROLE_USER"); var a = appointment();
        assertDoesNotThrow(() -> access.requireCanViewPreparation(a));
        assertDoesNotThrow(() -> access.requireCanViewFollowUp(a));
        assertThrows(AccessDeniedException.class, () -> access.requireCanViewCareSummary(a));
    }

    @Test void receptionAssignmentWithoutExecutionGrantCannotRevealTreatmentResponsesOrAftercare() {
        var permissions=org.mockito.Mockito.mock(com.core.beautyshop.modules.identity.api.StaffPermissionService.class);
        org.mockito.Mockito.when(permissions.has(org.mockito.ArgumentMatchers.anyString())).thenAnswer(call -> "SPA_RECEPTION".equals(call.getArgument(0)));
        org.springframework.test.util.ReflectionTestUtils.setField(access,"staffPermissions",permissions);
        actor(10L, "ROLE_STAFF"); var a = appointment();
        assertTrue(access.isAssignedTechnician(a));
        assertDoesNotThrow(() -> access.requireCanViewAppointment(a));
        assertThrows(AccessDeniedException.class, () -> access.requireCanViewPreparation(a));
        assertThrows(AccessDeniedException.class, () -> access.requireCanViewFollowUp(a));
        assertThrows(AccessDeniedException.class, () -> access.requireCanViewCareSummary(a));
        assertThrows(AccessDeniedException.class, () -> access.requireCanPerformItem(a.getItems().getFirst()));
    }

    @Test void assignedStaffAndAdminMayReadCareAndPerform() {
        var a = appointment();
        for (String role : List.of("ROLE_STAFF", "ROLE_ADMIN")) {
            actor(10L, role);
            assertDoesNotThrow(() -> access.requireCanViewPreparation(a));
            assertDoesNotThrow(() -> access.requireCanViewFollowUp(a));
            assertDoesNotThrow(() -> access.requireCanViewCareSummary(a));
            assertDoesNotThrow(() -> access.requireCanPerformItem(a.getItems().getFirst()));
        }
    }
}
