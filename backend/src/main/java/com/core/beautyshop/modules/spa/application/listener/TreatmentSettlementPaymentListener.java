package com.core.beautyshop.modules.spa.application.listener;
import com.core.beautyshop.modules.spa.api.event.TreatmentSettlementPaidEvent;
import com.core.beautyshop.modules.spa.application.service.TreatmentCourseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component @RequiredArgsConstructor @Slf4j
public class TreatmentSettlementPaymentListener {
    private final TreatmentCourseService treatments;
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void paid(TreatmentSettlementPaidEvent event) {
        try { treatments.reconcilePaidSettlement(event.changeId()); }
        catch (RuntimeException failure) {
            log.error("Paid treatment adjustment {} requires reconciliation", event.changeId(), failure);
        }
    }
}
