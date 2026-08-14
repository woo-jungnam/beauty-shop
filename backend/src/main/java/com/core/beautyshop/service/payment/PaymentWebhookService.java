package com.core.beautyshop.service.payment;

import com.core.beautyshop.dto.request.SePayWebhookRequest;

public interface PaymentWebhookService {
    void processSePayWebhook(SePayWebhookRequest request);
}
