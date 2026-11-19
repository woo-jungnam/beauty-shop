package com.core.beautyshop.modules.identity.application.service;

import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;

@Component
public class GoogleIdentityVerifier {
    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");
    private final String clientId;
    private final JwtDecoder decoder;

    @Autowired
    public GoogleIdentityVerifier(@Value("${GOOGLE_OAUTH_CLIENT_ID:}") String clientId) {
        this(clientId, NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs").build());
    }

    GoogleIdentityVerifier(String clientId, JwtDecoder decoder) {
        this.clientId = clientId.strip();
        this.decoder = decoder;
    }

    public GoogleIdentity verify(String credential) {
        if (clientId.isEmpty()) {
            throw new BusinessException(ErrorCode.GOOGLE_SIGN_IN_DISABLED);
        }
        Jwt token;
        try {
            token = decoder.decode(credential);
        } catch (JwtException ex) {
            throw new BusinessException(ErrorCode.INVALID_GOOGLE_TOKEN);
        }
        String issuer = token.getClaimAsString("iss");
        String subject = token.getSubject();
        String email = token.getClaimAsString("email");
        if (issuer == null || !ISSUERS.contains(issuer)
                || token.getAudience() == null || !token.getAudience().contains(clientId)
                || token.getExpiresAt() == null || !token.getExpiresAt().isAfter(Instant.now())
                || subject == null || !subject.matches("[!-~]{1,255}")
                || email == null || email.length() > 150 || !email.matches("[^\\s@]+@[^\\s@]+")
                || !Boolean.TRUE.equals(token.getClaims().get("email_verified"))) {
            throw new BusinessException(ErrorCode.INVALID_GOOGLE_TOKEN);
        }
        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        String hostedDomain = token.getClaimAsString("hd");
        boolean googleManagesEmail = normalizedEmail.endsWith("@gmail.com")
                || (hostedDomain != null && !hostedDomain.isBlank());
        return new GoogleIdentity(subject, normalizedEmail, token.getClaimAsString("name"), googleManagesEmail);
    }

    public record GoogleIdentity(String subject, String email, String name, boolean googleManagesEmail) {
    }
}
