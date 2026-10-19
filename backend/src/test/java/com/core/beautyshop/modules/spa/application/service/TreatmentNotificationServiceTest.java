package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.spa.domain.treatment.TreatmentNotification;
import com.core.beautyshop.modules.spa.domain.treatment.TreatmentNotificationRepository;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.shared.security.services.UserDetailsImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TreatmentNotificationServiceTest {
    @Mock TreatmentNotificationRepository rows;
    TreatmentNotificationService notifications;

    @BeforeEach void setup() {
        notifications = new TreatmentNotificationService(rows);
        var principal = new UserDetailsImpl(5L, "customer", "customer@example.test", "unused",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void recordDoesNotPublishInternalOrUnknownAuditEvents() {
        notifications.record(8L, 5L, "INTERNAL_ASSESSMENT", 3L);
        verifyNoInteractions(rows);
    }

    @Test void customerEventIsDeduplicatedByImmutableAuditIdAndContainsOnlyGenericText() {
        notifications.record(8L, 5L, "CHANGE_PROPOSED", 3L);
        var captured = org.mockito.ArgumentCaptor.forClass(TreatmentNotification.class);
        verify(rows).save(captured.capture());
        assertEquals("AUDIT:3", captured.getValue().getEventKey());
        assertEquals(5L, captured.getValue().getUserId());
        assertEquals("Mở hồ sơ lộ trình để xem thông tin và các bước tiếp theo.", captured.getValue().getMessage());
        when(rows.existsByEventKey("AUDIT:3")).thenReturn(true);
        notifications.record(8L, 5L, "CHANGE_PROPOSED", 3L);
        verify(rows, times(1)).save(any());
    }

    @Test void anotherCustomersNotificationCannotBeMarkedRead() {
        var row = new TreatmentNotification(); row.setId(9L); row.setUserId(6L);
        when(rows.findById(9L)).thenReturn(Optional.of(row));
        assertThrows(ResourceNotFoundException.class, () -> notifications.read(9L));
        assertNull(row.getReadAt());
    }

    @Test void readingAgainPreservesOriginalReadTime() {
        var row = new TreatmentNotification(); row.setId(9L); row.setUserId(5L);
        var original = Instant.parse("2026-10-16T01:00:00Z"); row.setReadAt(original);
        when(rows.findById(9L)).thenReturn(Optional.of(row));
        assertEquals(original, notifications.read(9L).readAt());
    }
}
