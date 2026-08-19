package com.core.beautyshop.modules.payment.api;

import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.modules.payment.application.dto.request.SePayWebhookRequest;
import com.core.beautyshop.modules.payment.application.service.PaymentWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Quản lý thanh toán", description = "API quản lý thanh toán")
@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentWebhookService paymentWebhookService;

    @Operation(summary = "Tiếp nhận webhook thanh toán tự động từ SePay")
    @PostMapping("/sepay-webhook")
    public ResponseEntity<ApiResponse<String>> handleSePayWebhook(
            @RequestBody SePayWebhookRequest request
    ) {
        paymentWebhookService.processSePayWebhook(request);
        return ResponseEntity.ok(ApiResponse.success("Webhook processed successfully"));
    }
}
