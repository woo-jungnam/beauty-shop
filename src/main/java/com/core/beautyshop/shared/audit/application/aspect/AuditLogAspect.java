package com.core.beautyshop.shared.audit.application.aspect;

import com.core.beautyshop.shared.audit.api.annotation.AuditAction;
import com.core.beautyshop.shared.audit.application.service.AuditLogWriter;
import com.core.beautyshop.shared.audit.domain.AuditLog;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
@org.springframework.core.annotation.Order(0)
public class AuditLogAspect {

    private final AuditLogWriter auditLogWriter;

    @Around("@annotation(auditAction)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, AuditAction auditAction) throws Throwable {
        long startTime = System.currentTimeMillis();
        String status = "SUCCESS";
        String errorMessage = null;
        Object result = null;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable ex) {
            status = "FAILED";
            errorMessage = ex.getMessage();
            throw ex;
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;
            try {
                saveAuditRecord(auditAction.action(), auditAction.resourceType(), joinPoint, status, errorMessage, executionTime);
            } catch (Exception e) {
                log.error("Lỗi khi lưu nhật ký kiểm toán (audit log) cho hành động={}: {}", auditAction.action(), e.getMessage());
            }
        }
    }

    @Around("execution(public * com.core.beautyshop..api..*(..)) && @within(org.springframework.web.bind.annotation.RestController) && !@annotation(com.core.beautyshop.shared.audit.api.annotation.AuditAction)")
    public Object auditAdminMutation(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) return joinPoint.proceed();
        HttpServletRequest request = attributes.getRequest();
        String method = request.getMethod();
        String uri = request.getServletPath();
        if (uri == null || uri.isEmpty()) uri = request.getRequestURI();
        boolean mutation = "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method);
        boolean managedPath = uri.startsWith("/api/v1/admin/") || uri.startsWith("/api/v1/users/admin/")
                || uri.startsWith("/api/v1/roles") || uri.startsWith("/api/v1/chatbot/sync/") || uri.startsWith("/api/v1/chatbot/test/")
                || java.util.Set.of("products", "categories", "brands", "attributes", "tags").stream()
                    .anyMatch(resource -> request.getRequestURI().matches(".*/api/v1/" + resource + "(?:/.*)?"))
                || uri.matches("/api/v1/auth/(login|logout|register|refresh)");
        if (!mutation || !managedPath) return joinPoint.proceed();
        long start = System.currentTimeMillis();
        String status = "SUCCESS";
        String error = null;
        try { return joinPoint.proceed(); }
        catch (Throwable ex) { status = "FAILED"; error = uri.startsWith("/api/v1/auth/") ? "Authentication operation failed" : ex.getMessage(); throw ex; }
        finally {
            try { saveAuditRecord(action(method, uri), resourceType(uri), joinPoint, status, error, System.currentTimeMillis() - start); }
            catch (Exception ex) { log.error("Unable to persist admin audit log: {}", ex.getMessage()); }
        }
    }

    private String resourceType(String uri) {
        String[] parts = uri.split("/");
        for (int i = 0; i < parts.length; i++) if ("admin".equals(parts[i]) && i + 1 < parts.length) return parts[i + 1].toUpperCase();
        for (int i = 0; i < parts.length; i++) if ("v1".equals(parts[i]) && i + 1 < parts.length) return parts[i + 1].toUpperCase(java.util.Locale.ROOT);
        return "SYSTEM";
    }

    private String action(String method, String uri) {
        if (uri.startsWith("/api/v1/auth/")) return "AUTH_" + uri.substring(uri.lastIndexOf('/') + 1).toUpperCase(java.util.Locale.ROOT);
        return "HTTP_" + method;
    }

    private void saveAuditRecord(String action, String resourceType, ProceedingJoinPoint joinPoint,
                                 String status, String errorMessage, long executionTime) {
        Long currentUserId = SecurityUtils.getCurrentUserIdOptional().orElse(null);
        String currentUsername = SecurityUtils.getCurrentUsernameOptional().orElse("ANONYMOUS");

        String ipAddress = "UNKNOWN";
        String userAgent = "UNKNOWN";

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            ipAddress = getClientIp(request);
            if (ipAddress.length() > 45) ipAddress = ipAddress.substring(0, 45);
            userAgent = request.getHeader("User-Agent");
            if (userAgent != null && userAgent.length() > 255) {
                userAgent = userAgent.substring(0, 255);
            }
        }

        String resourceId = null;
        Object[] args = joinPoint.getArgs();
        if (args != null && args.length > 0) {
            for (Object arg : args) {
                if (arg instanceof Long || arg instanceof String || arg instanceof Integer) {
                    resourceId = String.valueOf(arg);
                    break;
                }
            }
        }

        AuditLog auditLog = AuditLog.builder()
                .userId(currentUserId)
                .username(currentUsername)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .status(status)
                .errorMessage(errorMessage)
                .executionTimeMs(executionTime)
                .build();

        auditLogWriter.save(auditLog);
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty() || "unknown".equalsIgnoreCase(xfHeader)) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
