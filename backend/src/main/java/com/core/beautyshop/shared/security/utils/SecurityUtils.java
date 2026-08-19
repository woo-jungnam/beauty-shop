package com.core.beautyshop.shared.security.utils;

import com.core.beautyshop.shared.exception.UnauthorizedException;
import com.core.beautyshop.shared.security.services.UserDetailsImpl;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public class SecurityUtils {

    private SecurityUtils() {
        // Private constructor for utility class
    }

    /**
     * Lấy ID người dùng đang đăng nhập từ JWT Token (Ném UnauthorizedException nếu chưa đăng nhập)
     */
    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new UnauthorizedException("Bạn chưa đăng nhập hoặc phiên làm việc đã hết hạn. Vui lòng đăng nhập lại!");
        }
        if (auth.getPrincipal() instanceof UserDetailsImpl userDetails) {
            return userDetails.getId();
        }
        throw new UnauthorizedException("Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại!");
    }

    /**
     * Lấy ID người dùng (trả về Optional nếu là khách vãng lai)
     */
    public static Optional<Long> getCurrentUserIdOptional() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        if (auth.getPrincipal() instanceof UserDetailsImpl userDetails) {
            return Optional.ofNullable(userDetails.getId());
        }
        return Optional.empty();
    }

    /**
     * Lấy Username người dùng đang đăng nhập từ JWT Token
     */
    public static String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new UnauthorizedException("Bạn chưa đăng nhập hoặc phiên làm việc đã hết hạn. Vui lòng đăng nhập lại!");
        }
        return auth.getName();
    }

    /**
     * Kiểm tra xem user hiện tại có phải là ADMIN hay không
     */
    public static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
