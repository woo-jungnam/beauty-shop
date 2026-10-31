package com.core.beautyshop.shared.audit.application.service;

import com.core.beautyshop.shared.audit.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuditLogService {
    Page<AuditLog> list(String keyword, String action, String status, Pageable pageable);
    AuditLog getById(Long id);
}
