package com.core.beautyshop.shared.security.jwt;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class JwtAuthenticationFilterTest {

    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
            mock(JwtUtils.class), mock(AccessTokenRevocationChecker.class));

    @Test
    void skipsJwtParsingForSepayWebhookOnly() {
        MockHttpServletRequest webhook = new MockHttpServletRequest();
        webhook.setServletPath("/api/v1/payment/sepay-webhook");
        webhook.addHeader("Authorization", "Bearer sepay-api-key-not-a-jwt");

        MockHttpServletRequest regularApi = new MockHttpServletRequest();
        regularApi.setServletPath("/api/v1/orders/1");

        assertTrue(filter.shouldNotFilter(webhook));
        assertFalse(filter.shouldNotFilter(regularApi));
    }

    @Test
    void revokedBearerCannotKeepPreviousAuthenticationAndDownstreamRunsOnce() throws Exception {
        JwtUtils jwt = mock(JwtUtils.class);
        AccessTokenRevocationChecker revocations = mock(AccessTokenRevocationChecker.class);
        var principal = new com.core.beautyshop.shared.security.services.UserDetailsImpl(1L, "user", "user@example.com", "", java.util.List.of(), 0, "family");
        org.mockito.Mockito.when(jwt.parseAccessToken("revoked-token")).thenReturn(java.util.Optional.of(principal));
        var request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.addHeader("Authorization", "Bearer revoked-token");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        var context = org.springframework.security.core.context.SecurityContextHolder.getContext();
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("previous", "", java.util.List.of()));
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        try {
            new JwtAuthenticationFilter(jwt, revocations).doFilterInternal(request, response, (req, res) -> {
                calls.incrementAndGet();
                org.junit.jupiter.api.Assertions.assertNull(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
            });
            org.junit.jupiter.api.Assertions.assertEquals(1, calls.get());
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }
}
