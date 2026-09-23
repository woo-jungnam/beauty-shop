package com.core.beautyshop.shared.audit.application.aspect;

import com.core.beautyshop.shared.audit.api.annotation.AuditAction;
import com.core.beautyshop.shared.audit.application.service.AuditLogWriter;
import com.core.beautyshop.shared.audit.domain.AuditLog;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
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
}
