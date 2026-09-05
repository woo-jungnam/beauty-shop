package com.core.beautyshop.shared.audit.application.aspect;

import com.core.beautyshop.shared.audit.api.annotation.AuditAction;
import com.core.beautyshop.shared.audit.domain.AuditLog;
import com.core.beautyshop.shared.audit.domain.AuditLogRepository;
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
public class AuditLogAspect {

    private final AuditLogRepository auditLogRepository;

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
                saveAuditRecord(auditAction, joinPoint, status, errorMessage, executionTime);
            } catch (Exception e) {
                log.error("Lỗi khi lưu nhật ký kiểm toán (audit log) cho hành động={}: {}", auditAction.action(), e.getMessage());
            }
        }
    }

    private void saveAuditRecord(AuditAction auditAction, ProceedingJoinPoint joinPoint,
                                 String status, String errorMessage, long executionTime) {
        Long currentUserId = SecurityUtils.getCurrentUserIdOptional().orElse(null);
        String currentUsername = SecurityUtils.getCurrentUsernameOptional().orElse("ANONYMOUS");

        String ipAddress = "UNKNOWN";
        String userAgent = "UNKNOWN";

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            ipAddress = getClientIp(request);
            userAgent = request.getHeader("User-Agent");
            if (userAgent != null && userAgent.length() > 255) {
                userAgent = userAgent.substring(0, 255);
            }
        }

        String resourceId = null;
        Object[] args = joinPoint.getArgs();
        if (args != null && args.length > 0) {
            // Lấy ID đầu tiên nếu có truyền param ID
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
                .action(auditAction.action())
                .resourceType(auditAction.resourceType())
                .resourceId(resourceId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .status(status)
                .errorMessage(errorMessage)
                .executionTimeMs(executionTime)
                .build();

        auditLogRepository.save(auditLog);
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty() || "unknown".equalsIgnoreCase(xfHeader)) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
