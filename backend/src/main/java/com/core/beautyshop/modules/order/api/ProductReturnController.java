package com.core.beautyshop.modules.order.api;
import com.core.beautyshop.modules.order.application.service.ProductReturnService;
import com.core.beautyshop.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/orders/{orderId}/returns")
@Tag(name = "Yêu cầu đổi trả sản phẩm (Khách hàng)", description = "Khách hàng tra cứu và gửi yêu cầu đổi trả cho đơn hàng")
public class ProductReturnController {
    private final ProductReturnService service;

    @Operation(summary = "Xem các yêu cầu đổi trả của đơn hàng", description = "Khách hàng hoặc khách vãng lai tra cứu lịch sử yêu cầu đổi trả của đơn hàng")
    @GetMapping public ApiResponse<?> list(@PathVariable Long orderId,@RequestHeader(value="X-Guest-Session-Id",required=false)String session){return ApiResponse.success(service.customerList(orderId,session));}

    @Operation(summary = "Gửi yêu cầu đổi trả sản phẩm", description = "Khách hàng tạo yêu cầu đổi hàng hoặc trả hàng kèm lý do và bằng chứng hình ảnh")
    @PostMapping public ApiResponse<?> request(@PathVariable Long orderId,@RequestHeader(value="X-Guest-Session-Id",required=false)String session,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestBody ProductReturnService.Request command){return ApiResponse.success(service.request(orderId,session,key,command));}
}
