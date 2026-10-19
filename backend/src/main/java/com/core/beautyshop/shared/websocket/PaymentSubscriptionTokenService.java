package com.core.beautyshop.shared.websocket;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/** A short-lived order-scoped capability, never an account access token. */
@Service
public class PaymentSubscriptionTokenService {
    private final javax.crypto.SecretKey key;
    public PaymentSubscriptionTokenService(@Value("${jwt.secret}") String secret) {
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
    public Subscription issue(Long orderId, String orderNumber) {
        Instant expiry = Instant.now().plusSeconds(300);
        String token = Jwts.builder().subject(orderId.toString()).claim("purpose", "payment_subscription")
                .claim("orderNumber", orderNumber).issuedAt(new Date()).expiration(Date.from(expiry))
                .signWith(key).compact();
        return new Subscription(token, expiry, orderId);
    }
    public Optional<Scope> verify(String token) {
        if (token == null || token.length() > 4096) return Optional.empty();
        try {
            var c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            if (!"payment_subscription".equals(c.get("purpose", String.class))) return Optional.empty();
            long id = Long.parseLong(c.getSubject());
            String code = c.get("orderNumber", String.class);
            if (id <= 0 || code == null || code.isBlank()) return Optional.empty();
            return Optional.of(new Scope(id, code, c.getExpiration().toInstant()));
        } catch (RuntimeException ex) { return Optional.empty(); }
    }
    public record Subscription(String subscriptionToken, Instant expiresAt, Long orderId) {}
    public record Scope(Long orderId, String orderNumber, Instant expiresAt) {}
}
