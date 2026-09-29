package com.core.beautyshop.shared.maintenance;

import com.core.beautyshop.modules.identity.application.service.AuthService;
import com.core.beautyshop.modules.notification.application.service.NotificationService;
import com.core.beautyshop.shared.outbox.application.service.OutboxClaimService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataRetentionJobTest {

    @Mock
    private OutboxClaimService outboxClaimService;

    @Mock
    private AuthService authService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private DataRetentionJob dataRetentionJob;

    @Test
    void cleanupOldData_executesAllCleanupTasks() {
        when(outboxClaimService.cleanupPublishedMessages(14)).thenReturn(10);
        when(authService.cleanupExpiredSessions(14)).thenReturn(5);
        when(notificationService.cleanupOldReceipts(14)).thenReturn(20);

        dataRetentionJob.cleanupOldData();

        verify(outboxClaimService).cleanupPublishedMessages(14);
        verify(authService).cleanupExpiredSessions(14);
        verify(notificationService).cleanupOldReceipts(14);
    }
}
