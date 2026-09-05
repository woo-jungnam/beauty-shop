package com.core.beautyshop.modules.payment.api;

import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.modules.payment.application.dto.request.SePayWebhookRequest;
import com.core.beautyshop.modules.payment.application.service.PaymentWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Quản lý thanh toán", description = "API quản lý thanh toán")
@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentWebhookService paymentWebhookService;

    @Value("${sepay.webhook.api-key}")
    private String sePayWebhookApiKey;

    @Operation(summary = "Tiếp nhận webhook thanh toán tự động từ SePay")
    @PostMapping("/sepay-webhook")
    public ResponseEntity<ApiResponse<String>> handleSePayWebhook(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody SePayWebhookRequest request
    ) {
        if (!isValidWebhookApiKey(authorization)) {
            log.warn("Từ chối webhook: API Key không hợp lệ hoặc bị thiếu từ yêu cầu IP");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(HttpStatus.UNAUTHORIZED.value(),
                            "Không có quyền: API key webhook không hợp lệ", null));
        }

        paymentWebhookService.processSePayWebhook(request);
        return ResponseEntity.ok(ApiResponse.success("Xử lý webhook thành công"));
    }

    private boolean isValidWebhookApiKey(String authorization) {
        if (authorization == null || authorization.isBlank() || sePayWebhookApiKey == null) {
            return false;
        }

        String apiKey;
        if (authorization.toLowerCase().startsWith("apikey ")) {
            apiKey = authorization.substring(7).trim();
        } else if (authorization.toLowerCase().startsWith("bearer ")) {
            apiKey = authorization.substring(7).trim();
        } else {
            apiKey = authorization.trim();
        }

        return java.security.MessageDigest.isEqual(
                sePayWebhookApiKey.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                apiKey.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }
}
