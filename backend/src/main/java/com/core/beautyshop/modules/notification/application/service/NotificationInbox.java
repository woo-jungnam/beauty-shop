package com.core.beautyshop.modules.notification.application.service;
import com.core.beautyshop.modules.notification.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor
public class NotificationInbox {
    private final NotificationReceiptRepository receipts;
    private final jakarta.persistence.EntityManager entityManager;
    @Transactional
    public void process(String eventId, Runnable action) {
        if (receipts.existsById(eventId)) return;
        var receipt = new NotificationReceipt();
        receipt.setEventId(eventId);
        entityManager.persist(receipt);
        entityManager.flush();
        action.run();
    }
}
