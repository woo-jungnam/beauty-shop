package com.core.beautyshop.shared.audit.application.service;

import com.core.beautyshop.shared.audit.domain.AuditLog;
import com.core.beautyshop.shared.audit.domain.AuditLogRepository;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {
    private final AuditLogRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLog> list(String keyword, String action, String status, Pageable pageable) {
        Specification<AuditLog> spec = Specification.where(null);
        if (keyword != null && !keyword.isBlank()) {
            String value = "%" + keyword.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("username")), value),
                    cb.like(cb.lower(root.get("resourceType")), value),
                    cb.like(cb.lower(root.get("resourceId")), value)));
        }
        if (action != null && !action.isBlank()) spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), action));
        if (status != null && !status.isBlank()) spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        return repository.findAll(spec, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public AuditLog getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Audit log not found: " + id));
    }
}
