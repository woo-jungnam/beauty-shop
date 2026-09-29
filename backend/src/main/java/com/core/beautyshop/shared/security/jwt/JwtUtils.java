package com.core.beautyshop.shared.security.jwt;

import com.core.beautyshop.shared.security.services.UserDetailsImpl;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.time.Instant;
import java.util.UUID;
import java.util.Optional;

@Component
@Slf4j
public class JwtUtils {

    private static final String TOKEN_USE_CLAIM = "token_use";
    private static final String TOKEN_VERSION_CLAIM = "token_version";
    private static final String ACCESS_TOKEN_USE = "access";
    private static final String REFRESH_TOKEN_USE = "refresh";

    @Value("${jwt.secret:beautyshop_jwt_secret_key_must_be_at_least_256_bits_long_1234567890!}")
    private String jwtSecret;

    @Value("${jwt.expiration-ms:900000}")
    private long jwtExpirationMs;

    @Value("${jwt.refresh-expiration-ms:604800000}")
    private long jwtRefreshExpirationMs;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UserDetailsImpl userPrincipal) {
        List<String> roles = userPrincipal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return Jwts.builder()
                .subject(userPrincipal.getUsername())
                .claim("id", userPrincipal.getId())
                .claim("email", userPrincipal.getEmail())
                .claim("roles", roles)
                .claim(TOKEN_VERSION_CLAIM, userPrincipal.getTokenVersion())
                .claim(TOKEN_USE_CLAIM, ACCESS_TOKEN_USE)
                .issuedAt(new Date())
                .expiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(key())
                .compact();
    }

    public String generateRefreshToken(String username) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(username)
                .claim(TOKEN_USE_CLAIM, REFRESH_TOKEN_USE)
                .issuedAt(new Date())
                .expiration(new Date((new Date()).getTime() + jwtRefreshExpirationMs))
                .signWith(key())
                .compact();
    }

    public Instant getExpirationInstant(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration()
                .toInstant();
    }

    public String getUserNameFromJwtToken(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public UserDetailsImpl getUserPrincipalFromJwtToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return toUserPrincipal(claims);
    }

    public Optional<UserDetailsImpl> parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!ACCESS_TOKEN_USE.equals(claims.get(TOKEN_USE_CLAIM, String.class))) {
                return Optional.empty();
            }
            return Optional.of(toUserPrincipal(claims));
        } catch (JwtException | IllegalArgumentException exception) {
            log.debug("Invalid access token: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    private UserDetailsImpl toUserPrincipal(Claims claims) {

        String username = claims.getSubject();
        Long id = null;
        Object idObj = claims.get("id");
        if (idObj instanceof Number number) {
            id = number.longValue();
        } else if (idObj instanceof String idStr) {
            id = Long.parseLong(idStr);
        }

        String email = (String) claims.get("email");

        List<GrantedAuthority> authorities = List.of();
        Object rolesObj = claims.get("roles");
        if (rolesObj instanceof List<?> roleList) {
            authorities = roleList.stream()
                    .map(Object::toString)
                    .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                    .collect(java.util.stream.Collectors.toList());
        }

        Integer tokenVersion = claims.get(TOKEN_VERSION_CLAIM, Integer.class);

        return new UserDetailsImpl(
                id,
                username,
                email,
                "",
                authorities,
                tokenVersion
        );
    }

    public boolean validateAccessToken(String token) {
        return validateTokenUse(token, ACCESS_TOKEN_USE);
    }

    public boolean validateRefreshToken(String token) {
        return validateTokenUse(token, REFRESH_TOKEN_USE);
    }

    private boolean validateTokenUse(String token, String expectedUse) {
        if (!validateJwtToken(token)) {
            return false;
        }
        Claims claims = Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
        return expectedUse.equals(claims.get(TOKEN_USE_CLAIM, String.class));
    }

    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parser().verifyWith(key()).build().parseSignedClaims(authToken);
            return true;
        } catch (MalformedJwtException e) {
            log.error("Token JWT không đúng định dạng: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("Token JWT đã hết hạn: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("Token JWT không được hỗ trợ: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("Chuỗi JWT claims rỗng: {}", e.getMessage());
        }
        return false;
    }
}
