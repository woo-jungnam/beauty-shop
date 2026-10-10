package com.core.beautyshop.shared.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.core.beautyshop.shared.security.services.UserDetailsImpl;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final AccessTokenRevocationChecker accessTokenRevocationChecker;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return "/api/v1/payment/sepay-webhook".equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String jwt = parseJwt(request);
            // A supplied bearer token is authoritative; never retain an earlier thread/test authentication.
            if (jwt != null) SecurityContextHolder.clearContext();
            java.util.Optional<UserDetailsImpl> principal = jwt == null
                    ? java.util.Optional.empty()
                    : jwtUtils.parseAccessToken(jwt);
            if (principal.isPresent()) {
                UserDetailsImpl userDetails = principal.get();
                if (!accessTokenRevocationChecker.isCurrent(
                        userDetails.getId(), userDetails.getTokenVersion())
                        || !accessTokenRevocationChecker.isSessionCurrent(userDetails.getId(), userDetails.getSessionFamilyId())) {
                    log.debug("Access token đã bị thu hồi cho userId={}", userDetails.getId());
                } else {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            log.error("Không thể thiết lập xác thực người dùng: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");

        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }

        return null;
    }
}
