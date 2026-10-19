package com.core.beautyshop.shared.websocket;

import com.core.beautyshop.modules.order.application.service.OrderService;
import com.core.beautyshop.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;

@RestController @RequiredArgsConstructor
public class PaymentSubscriptionController {
    private final OrderService orders;
    private final PaymentSubscriptionTokenService tokens;

    @GetMapping("/api/v1/orders/{id}/payment-subscription")
    @Operation(summary="Cấp quyền theo dõi thanh toán 5 phút", description="Chỉ chủ đơn, guest session đúng hoặc ADMIN; token chỉ theo dõi đúng đơn, không dùng làm access token")
    public ApiResponse<PaymentSubscriptionTokenService.Subscription> issue(@PathVariable Long id,
            @RequestHeader(value="X-Guest-Session-Id",required=false) String guestSession) {
        var order = orders.getOrderById(id, guestSession);
        return ApiResponse.success(tokens.issue(order.getId(), order.getOrderNumber()));
    }
}
