package com.core.beautyshop.modules.payment.application.service;

import com.core.beautyshop.modules.payment.application.dto.request.SePayWebhookRequest;

public interface PaymentWebhookService {
    void processSePayWebhook(SePayWebhookRequest request);
}
