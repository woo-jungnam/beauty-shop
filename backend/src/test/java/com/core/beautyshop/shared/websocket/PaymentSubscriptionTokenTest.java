package com.core.beautyshop.shared.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentSubscriptionTokenTest {
    private static final String SECRET = "test_payment_subscription_secret_more_than_sixty_four_bytes_1234567890";
    private final PaymentSubscriptionTokenService tokens = new PaymentSubscriptionTokenService(SECRET);

    String signed(String purpose, String subject, String code, Instant expiry, String secret) {
        var builder = Jwts.builder().subject(subject).claim("purpose", purpose).claim("orderNumber", code);
        if (expiry != null) builder.expiration(Date.from(expiry));
        return builder.signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }

    @Test void issuedCapabilityIsOrderScopedAndExpiresAfterFiveMinutes() {
        Instant before = Instant.now();
        var subscription = tokens.issue(7L, "ORD-SEVEN");
        var scope = tokens.verify(subscription.subscriptionToken()).orElseThrow();
        assertEquals(7L, scope.orderId()); assertEquals("ORD-SEVEN", scope.orderNumber());
        assertEquals(7L, subscription.orderId());
        assertTrue(subscription.expiresAt().isAfter(before.plusSeconds(299)));
        assertTrue(subscription.expiresAt().isBefore(Instant.now().plusSeconds(301)));
        // JWT timestamps have whole-second precision.
        assertTrue(Math.abs(scope.expiresAt().getEpochSecond() - subscription.expiresAt().getEpochSecond()) <= 1);
    }

    @Test void rejectsAccessTokensExpiredTokensInvalidScopesAndOtherSigningKeys() {
        Instant future = Instant.now().plusSeconds(100);
        assertTrue(tokens.verify(null).isEmpty()); assertTrue(tokens.verify("").isEmpty());
        assertTrue(tokens.verify("not.a.jwt").isEmpty()); assertTrue(tokens.verify("x".repeat(4097)).isEmpty());
        assertTrue(tokens.verify(signed("access", "7", "ORD-SEVEN", future, SECRET)).isEmpty());
        String accountToken = Jwts.builder().subject("customer").claim("id", 7).claim("token_use", "access")
            .expiration(Date.from(future)).signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        assertTrue(tokens.verify(accountToken).isEmpty());
        assertTrue(tokens.verify(signed("payment_subscription", "7", "ORD-SEVEN", Instant.now().minusSeconds(5), SECRET)).isEmpty());
        assertTrue(tokens.verify(signed("payment_subscription", "7", "ORD-SEVEN", null, SECRET)).isEmpty());
        assertTrue(tokens.verify(signed("payment_subscription", "0", "ORD-SEVEN", future, SECRET)).isEmpty());
        assertTrue(tokens.verify(signed("payment_subscription", "not-an-id", "ORD-SEVEN", future, SECRET)).isEmpty());
        assertTrue(tokens.verify(signed("payment_subscription", "7", " ", future, SECRET)).isEmpty());
        assertTrue(tokens.verify(signed("payment_subscription", "7", "ORD-SEVEN", future,
            "a_different_test_signing_key_that_is_also_longer_than_sixty_four_bytes_12345")).isEmpty());
    }

    @Test void handshakeRefusesGuessableOrderParametersAndIgnoresMismatchedParametersBesideValidToken() {
        var handshake = new PaymentSubscriptionHandshake(tokens);
        var request = new MockHttpServletRequest("GET", "/ws/payment");
        request.setQueryString("orderId=7&orderCode=ORD-SEVEN");
        var response = new MockHttpServletResponse();
        assertFalse(handshake.beforeHandshake(new ServletServerHttpRequest(request), new ServletServerHttpResponse(response), mock(WebSocketHandler.class), new HashMap<>()));
        assertEquals(HttpStatus.FORBIDDEN.value(), response.getStatus());
        var capability = tokens.issue(7L, "ORD-SEVEN");
        request.setQueryString("subscriptionToken=" + capability.subscriptionToken() + "&orderId=99&orderCode=ORD-OTHER");
        var attributes = new HashMap<String,Object>();
        assertTrue(handshake.beforeHandshake(new ServletServerHttpRequest(request), new ServletServerHttpResponse(new MockHttpServletResponse()), mock(WebSocketHandler.class), attributes));
        var scope = (PaymentSubscriptionTokenService.Scope) attributes.get("paymentScope");
        assertEquals(7L, scope.orderId()); assertEquals("ORD-SEVEN", scope.orderNumber());
    }

    @Test void websocketOnlyDeliversToVerifiedOrderAndExpiredScopeIsClosed() throws Exception {
        var handler = new PaymentWebSocketHandler(new ObjectMapper());
        var session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("verified-session"); when(session.isOpen()).thenReturn(true);
        // An attacker-controlled URI never overrides the verified handshake attributes.
        when(session.getUri()).thenReturn(URI.create("ws://localhost/ws/payment?orderId=99&orderCode=ORD-OTHER"));
        when(session.getAttributes()).thenReturn(Map.of("paymentScope", new PaymentSubscriptionTokenService.Scope(7L, "ORD-SEVEN", Instant.now().plusSeconds(300))));
        handler.afterConnectionEstablished(session); clearInvocations(session);
        handler.broadcastPaymentSuccess("ORD-OTHER", 99L, BigDecimal.TEN);
        verify(session, never()).sendMessage(any());
        handler.broadcastPaymentSuccess("ORD-SEVEN", 7L, BigDecimal.ONE);
        verify(session, times(1)).sendMessage(any(TextMessage.class));
        handler.afterConnectionClosed(session, CloseStatus.NORMAL); clearInvocations(session);
        handler.broadcastPaymentSuccess("ORD-SEVEN", 7L, BigDecimal.ONE);
        verify(session, never()).sendMessage(any());

        var expired = mock(WebSocketSession.class);
        when(expired.getAttributes()).thenReturn(Map.of("paymentScope", new PaymentSubscriptionTokenService.Scope(7L, "ORD-SEVEN", Instant.now().minusSeconds(1))));
        handler.afterConnectionEstablished(expired);
        verify(expired).close(CloseStatus.POLICY_VIOLATION);
        verify(expired, never()).sendMessage(any());
    }
}
