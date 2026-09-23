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
}
