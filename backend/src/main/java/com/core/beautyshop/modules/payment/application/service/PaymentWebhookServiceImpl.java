package com.core.beautyshop.modules.payment.application.service;
import com.core.beautyshop.modules.payment.application.dto.request.SePayWebhookRequest;
import com.core.beautyshop.modules.payment.domain.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class PaymentWebhookServiceImpl implements PaymentWebhookService {
    private final PaymentWebhookProcessor processor;
    private final PaymentTransactionRepository transactions;

    @Override
    public void processSePayWebhook(SePayWebhookRequest request) {
        String reference = processor.reference(request);
        for (int attempt = 0; ; attempt++) {
            try { processor.process(request, reference); return; }
            catch (DataIntegrityViolationException exception) {
                if (attempt >= 2 || !transactions.existsByReferenceCode(reference)) throw exception;
            }
            catch (org.springframework.dao.TransientDataAccessException exception) {
                if (attempt >= 2) throw exception;
            }
        }
    }
}
