package com.core.beautyshop.shared.audit.application.aspect;

import com.core.beautyshop.shared.audit.api.annotation.AuditAction;
import com.core.beautyshop.shared.audit.application.service.AuditLogWriter;
import com.core.beautyshop.shared.audit.domain.AuditLog;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogAspectTest {
    @AfterEach
    void clearRequestContext() { org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes(); }

    @Mock
    private AuditLogWriter auditLogWriter;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private AuditAction auditAction;

    @InjectMocks
    private AuditLogAspect auditLogAspect;

    @Test
    void writesFailedAuditRecordAndRethrowsOriginalException() throws Throwable {
        IllegalStateException failure = new IllegalStateException("operation failed");
        when(joinPoint.proceed()).thenThrow(failure);
        when(joinPoint.getArgs()).thenReturn(new Object[]{42L});
        when(auditAction.action()).thenReturn("UPDATE_ORDER");
        when(auditAction.resourceType()).thenReturn("ORDER");

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> auditLogAspect.auditMethod(joinPoint, auditAction)
        );

        assertSame(failure, thrown);
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogWriter).save(captor.capture());
        AuditLog auditLog = captor.getValue();
        assertEquals("FAILED", auditLog.getStatus());
        assertEquals("operation failed", auditLog.getErrorMessage());
        assertEquals("42", auditLog.getResourceId());
    }

    @Test
    void auditsAllLegacyAdminMutationRoutesAndAuthWithoutRequestBodies() throws Throwable {
        when(joinPoint.proceed()).thenReturn("done");
        when(joinPoint.getArgs()).thenReturn(new Object[]{42L});
        String[] paths = {"/api/v1/products/42", "/api/v1/categories/42", "/api/v1/brands/42", "/api/v1/attributes/42",
                "/api/v1/tags/42", "/api/v1/users/admin/42/force-logout", "/api/v1/chatbot/sync/database", "/api/v1/auth/login"};
        for (String path : paths) {
            var request = new org.springframework.mock.web.MockHttpServletRequest("POST", path);
            org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
            assertEquals("done", auditLogAspect.auditAdminMutation(joinPoint));
        }
        ArgumentCaptor<AuditLog> logs = ArgumentCaptor.forClass(AuditLog.class);
        org.mockito.Mockito.verify(auditLogWriter, org.mockito.Mockito.times(paths.length)).save(logs.capture());
        assertEquals("PRODUCTS", logs.getAllValues().getFirst().getResourceType());
        assertEquals("AUTH_LOGIN", logs.getAllValues().getLast().getAction());
    }

    @Test
    void authFailureAuditDoesNotExposeErrorCredentials() throws Throwable {
        var request = new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/v1/auth/login");
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
        when(joinPoint.proceed()).thenThrow(new IllegalArgumentException("password=value-must-not-be-audited"));
        when(joinPoint.getArgs()).thenReturn(new Object[]{});
        assertThrows(IllegalArgumentException.class, () -> auditLogAspect.auditAdminMutation(joinPoint));
        ArgumentCaptor<AuditLog> log = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogWriter).save(log.capture());
        assertEquals("FAILED", log.getValue().getStatus());
        assertEquals("Authentication operation failed", log.getValue().getErrorMessage());
    }

    @Test
    void publicReadsAndCustomerMutationsDoNotCreateAdminAuditLogs() throws Throwable {
        when(joinPoint.proceed()).thenReturn("done");
        for (String[] route : new String[][]{{"GET", "/api/v1/products/42"}, {"POST", "/api/v1/cart"}}) {
            var request = new org.springframework.mock.web.MockHttpServletRequest(route[0], route[1]);
            org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
            auditLogAspect.auditAdminMutation(joinPoint);
        }
        org.mockito.Mockito.verifyNoInteractions(auditLogWriter);
    }
}
