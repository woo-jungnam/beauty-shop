package com.core.beautyshop.modules.payment.api;

import com.core.beautyshop.modules.payment.application.dto.request.SePayWebhookRequest;
import com.core.beautyshop.modules.payment.application.service.PaymentWebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentControllerTest {

    private PaymentWebhookService webhookService;
    private PaymentController controller;

    @BeforeEach
    void setUp() {
        webhookService = mock(PaymentWebhookService.class);
        controller = new PaymentController(webhookService);
        ReflectionTestUtils.setField(controller, "sePayWebhookApiKey", "secret-test-key-123");
    }

    @Test
    void validApiKeyWithPrefixPasses() {
        var request = new SePayWebhookRequest();
        var response = controller.handleSePayWebhook("Apikey secret-test-key-123", request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(webhookService, times(1)).processSePayWebhook(request);
    }

    @Test
    void validApiKeyWithBearerPrefixPasses() {
        var request = new SePayWebhookRequest();
        var response = controller.handleSePayWebhook("Bearer secret-test-key-123", request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(webhookService, times(1)).processSePayWebhook(request);
    }

    @Test
    void validRawApiKeyPasses() {
        var request = new SePayWebhookRequest();
        var response = controller.handleSePayWebhook("secret-test-key-123", request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(webhookService, times(1)).processSePayWebhook(request);
    }

    @Test
    void invalidApiKeyIsRejected() {
        var request = new SePayWebhookRequest();
        var response = controller.handleSePayWebhook("Apikey wrong-key", request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(webhookService, never()).processSePayWebhook(any());
    }

    @Test
    void missingAuthorizationHeaderIsRejected() {
        var request = new SePayWebhookRequest();
        var response = controller.handleSePayWebhook(null, request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(webhookService, never()).processSePayWebhook(any());
    }

    @Test
    void blankAuthorizationHeaderIsRejected() {
        var request = new SePayWebhookRequest();
        var response = controller.handleSePayWebhook("   ", request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(webhookService, never()).processSePayWebhook(any());
    }

    @Test
    void blankServerApiKeyRejectsAnyAuthorization() {
        ReflectionTestUtils.setField(controller, "sePayWebhookApiKey", "");

        var request = new SePayWebhookRequest();
        var response = controller.handleSePayWebhook("Apikey ", request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(webhookService, never()).processSePayWebhook(any());
    }
}
