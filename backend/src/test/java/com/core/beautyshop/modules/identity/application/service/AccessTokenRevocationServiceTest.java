package com.core.beautyshop.modules.identity.application.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessTokenRevocationServiceTest {

    @Mock
    private TokenVersionCache tokenVersionCache;

    @InjectMocks
    private AccessTokenRevocationService service;

    @Test
    void acceptsOnlyCurrentTokenVersion() {
        when(tokenVersionCache.findCurrentVersion(10L)).thenReturn(Optional.of(3));

        assertTrue(service.isCurrent(10L, 3));
        assertFalse(service.isCurrent(10L, 2));
        assertFalse(service.isCurrent(10L, null));
    }

    @Test
    void rejectsDeletedOrMissingUser() {
        when(tokenVersionCache.findCurrentVersion(10L)).thenReturn(Optional.empty());

        assertFalse(service.isCurrent(10L, 0));
    }
}
