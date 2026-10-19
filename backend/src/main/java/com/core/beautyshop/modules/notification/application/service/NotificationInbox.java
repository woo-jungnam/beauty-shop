package com.core.beautyshop.modules.notification.application.service;

import com.core.beautyshop.modules.notification.domain.NotificationReceipt;
import com.core.beautyshop.modules.notification.domain.NotificationReceiptRepository;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class NotificationInbox {
    private final NotificationReceiptRepository receipts;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    public NotificationInbox(NotificationReceiptRepository receipts,
                             EntityManager entityManager,
                             PlatformTransactionManager transactionManager) {
        this.receipts = receipts;
        this.entityManager = entityManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void process(String eventId, Runnable action) {
        if (eventId == null || receipts.existsById(eventId)) return;
        try {
            transactionTemplate.executeWithoutResult(status -> {
                if (receipts.existsById(eventId)) return;
                var receipt = new NotificationReceipt();
                receipt.setEventId(eventId);
                entityManager.persist(receipt);
                entityManager.flush();
                action.run();
            });
        } catch (DataIntegrityViolationException ex) {
            log.info("Duplicate notification receipt detected concurrently for eventId={}, skipping.", eventId);
        }
    }
}
