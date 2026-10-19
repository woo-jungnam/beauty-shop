package com.core.beautyshop.modules.order.api;
import com.core.beautyshop.modules.order.application.service.ProductReturnService;
import com.core.beautyshop.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/orders/{orderId}/returns")
public class ProductReturnController {
    private final ProductReturnService service;
    @GetMapping public ApiResponse<?> list(@PathVariable Long orderId,@RequestHeader(value="X-Guest-Session-Id",required=false)String session){return ApiResponse.success(service.customerList(orderId,session));}
    @PostMapping public ApiResponse<?> request(@PathVariable Long orderId,@RequestHeader(value="X-Guest-Session-Id",required=false)String session,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestBody ProductReturnService.Request command){return ApiResponse.success(service.request(orderId,session,key,command));}
}
