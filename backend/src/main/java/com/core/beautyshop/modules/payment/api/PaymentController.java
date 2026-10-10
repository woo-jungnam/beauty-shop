package com.core.beautyshop.modules.payment.api;

import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.modules.payment.application.dto.request.SePayWebhookRequest;
import com.core.beautyshop.modules.payment.application.service.PaymentWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Webhook thanh toán SePay", description = "Tuyến tích hợp máy chủ SePay, xác thực bằng API key webhook riêng; không dùng JWT khách hàng")
@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentWebhookService paymentWebhookService;

    @Value("${sepay.webhook.api-key:your-sepay-api-key-here}")
    private String sePayWebhookApiKey;

    @Operation(summary = "Tiếp nhận webhook SePay", description = "Nhập Authorization dạng 'Apikey <key>' trong scheme sepayApiKey; controller cũng chấp nhận Bearer hoặc raw API key, đây vẫn là key webhook chứ không phải JWT. ReferenceCode hoặc id dương của nhà cung cấp là khóa gửi lại; trùng giao dịch không cộng tiền lần hai. HTTP 200 xác nhận xử lý, không bảo đảm đơn đã PAID: in phải dương, nguyên VND và đúng tài khoản; sai điều kiện ghi FAILED, out ghi IGNORED. Nội dung cần mã ORD để khớp đơn; tiền thiếu ghi PARTIALLY_PAID, tiền đến đơn hủy/trả/visit đã hoàn chờ đối soát hoàn. Gateway ledger cố định SEPAY, payload.gateway chỉ metadata.")
    @SecurityRequirement(name = "sepayApiKey")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Đã xử lý hoặc nhận lại giao dịch trùng; xem ledger để biết kết quả tiền/đơn"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Thiếu hoặc sai API key webhook"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Body không đọc được hoặc thiếu định danh giao dịch ổn định")
    })
    @PostMapping("/sepay-webhook")
    public ResponseEntity<ApiResponse<String>> handleSePayWebhook(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
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
