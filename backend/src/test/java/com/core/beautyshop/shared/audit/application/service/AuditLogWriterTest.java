package com.core.beautyshop.shared.audit.application.service;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AuditLogWriterTest {

    @Test
    void saveAlwaysUsesANewTransaction() throws NoSuchMethodException {
        Transactional transactional = AuditLogWriter.class
                .getMethod("save", com.core.beautyshop.shared.audit.domain.AuditLog.class)
                .getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
    }
}
