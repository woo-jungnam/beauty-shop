package com.core.beautyshop.modules.catalog.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Reject oversize/unknown-length bodies before the servlet multipart parser allocates files. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ImageSearchUploadFilter extends OncePerRequestFilter {
    private static final long MAX_REQUEST = 5L * 1024 * 1024 + 64 * 1024;

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod())
                || !"/api/v1/products/search-by-image".equals(request.getServletPath());
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                             FilterChain chain) throws ServletException, IOException {
        long length = request.getContentLengthLong();
        if (length < 0 || request.getHeader("Transfer-Encoding") != null) {
            reject(response, 411, "Cần Content-Length hợp lệ khi tải ảnh.");
        } else if (length > MAX_REQUEST) {
            reject(response, 413, "Ảnh tối đa 5 MiB; yêu cầu multipart vượt giới hạn cho phép.");
        } else {
            chain.doFilter(request, response);
        }
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":" + status + ",\"errorCode\":\"IMAGE_SEARCH_" + status
                + "\",\"message\":\"" + message + "\"}");
    }
}
