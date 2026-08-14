package com.core.beautyshop.controller;

import io.swagger.v3.oas.annotations.tags.Tag;


import com.core.beautyshop.dto.common.ApiResponse;
import com.core.beautyshop.dto.request.SePayWebhookRequest;
import com.core.beautyshop.service.payment.PaymentWebhookService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Quản lý thanh toán", description = "API quản lý thanh toán")
@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentWebhookService paymentWebhookService;

    @PostMapping("/sepay-webhook")
    public ResponseEntity<ApiResponse<String>> handleSePayWebhook(
            @RequestBody SePayWebhookRequest request,
            HttpServletRequest httpServletRequest
    ) {
        log.info("Incoming Webhook from SePay...");
        
        try {
            paymentWebhookService.processSePayWebhook(request);
            return ResponseEntity.ok(ApiResponse.success("Webhook processed successfully", httpServletRequest.getRequestURI()));
        } catch (Exception e) {
            log.error("Error processing SePay webhook: ", e);
            // We usually still return 200 OK to SePay so they don't retry endlessly,
            // or 400 if it's a known bad request. But returning 200 is safer for webhooks if we logged the error.
            return ResponseEntity.ok(ApiResponse.success("Webhook received with errors (check logs)", httpServletRequest.getRequestURI()));
        }
    }
}
