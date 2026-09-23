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
        try { processor.process(request, reference); }
        catch (DataIntegrityViolationException exception) {
            // Processor's transaction has already rolled back. A concurrent committed duplicate is harmless.
            if (!transactions.existsByReferenceCode(reference)) throw exception;
        }
    }
}
