package com.core.beautyshop.shared.security;

import com.core.beautyshop.modules.identity.api.StaffPermissionService;
import com.core.beautyshop.shared.security.services.UserDetailsImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StaffPermissionInterceptorTest {
    private final StaffPermissionService permissions = mock(StaffPermissionService.class);
    private final StaffPermissionInterceptor interceptor = new StaffPermissionInterceptor(permissions);
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    void actor(String role) {
        var principal = new UserDetailsImpl(7L, "staff", "staff@example.test", "unused", List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
    boolean request(String method, String path) {
        return interceptor.preHandle(new MockHttpServletRequest(method, path), new MockHttpServletResponse(), new Object());
    }
    @Test void catalogGrantCannotReadOrdersOrMutateStockAndAdminRemainsAuthorized() {
        actor("ROLE_STAFF"); when(permissions.has("CATALOG")).thenReturn(true);
        assertTrue(request("PUT", "/api/v1/admin/products/7"));
        assertThrows(AccessDeniedException.class, () -> request("GET", "/api/v1/admin/orders"));
        assertThrows(AccessDeniedException.class, () -> request("POST", "/api/v1/admin/inventory/inbound"));
        assertThrows(AccessDeniedException.class, () -> request("PUT", "/api/v1/orders/7/status"));
        actor("ROLE_ADMIN"); clearInvocations(permissions);
        assertTrue(request("POST", "/api/v1/admin/inventory/inbound")); verifyNoInteractions(permissions);
    }
    @Test void executionGrantAllowsOperationalTreatmentWorkspaceButDoesNotImplyReceptionOrReports() {
        actor("ROLE_STAFF"); when(permissions.has("SPA_EXECUTION")).thenReturn(true);
        assertTrue(request("GET", "/api/v1/staff/spa/treatments/7"));
        assertTrue(request("PUT", "/api/v1/appointments/7/items/2/execution"));
        assertTrue(request("GET", "/api/v1/appointments/admin/all"));
        assertTrue(request("PUT", "/api/v1/appointments/admin/7/status"));
        assertThrows(AccessDeniedException.class, () -> request("POST", "/api/v1/appointments/7/invoice"));
        assertThrows(AccessDeniedException.class, () -> request("PUT", "/api/v1/appointments/7/check-in"));
        assertThrows(AccessDeniedException.class, () -> request("GET", "/api/v1/admin/dashboard"));
    }
    @Test void grantChecksRespectContextPathAndPreflightAndLeaveOwnerChecksToCustomerServices() {
        actor("ROLE_STAFF");
        var request = new MockHttpServletRequest("GET", "/beautyshop/api/v1/admin/orders"); request.setContextPath("/beautyshop");
        assertThrows(AccessDeniedException.class, () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertTrue(request("OPTIONS", "/api/v1/admin/orders"));
        actor("ROLE_CUSTOMER"); clearInvocations(permissions);
        assertTrue(request("DELETE", "/api/v1/orders/7/cancel"));
        assertTrue(request("PUT", "/api/v1/appointments/7/reschedule")); verifyNoInteractions(permissions);
    }
}
