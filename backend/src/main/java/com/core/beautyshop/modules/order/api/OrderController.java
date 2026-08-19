package com.core.beautyshop.modules.order.api;

import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.shared.dto.PageResponse;
import com.core.beautyshop.modules.order.application.dto.request.CheckoutRequest;
import com.core.beautyshop.modules.order.application.dto.request.UpdateOrderStatusRequest;
import com.core.beautyshop.modules.order.application.dto.response.OrderResponse;
import com.core.beautyshop.modules.order.application.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Quản lý đơn hàng", description = "API quản lý đơn hàng")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "Thanh toán & tạo đơn hàng mới")
    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<OrderResponse>> checkout(
            @Valid @RequestBody CheckoutRequest request) {
        OrderResponse order = orderService.checkout(request);
        return ResponseEntity.status(201).body(ApiResponse.created(order, "Tạo đơn hàng thành công"));
    }

    @Operation(summary = "Xem chi tiết đơn hàng")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrderById(id)));
    }

    @Operation(summary = "Xem danh sách đơn hàng của tôi")
    @GetMapping("/my-orders")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getMyOrders(
            @PageableDefault(size = 20) Pageable pageable) {
        Page<OrderResponse> page = orderService.getMyOrders(pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(page)));
    }

    @Operation(summary = "Xem đơn hàng theo User ID (Admin hoặc chính chủ)")
    @GetMapping("/user/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getOrdersByUser(
            @PathVariable Long userId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<OrderResponse> page = orderService.getOrdersByUser(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(page)));
    }

    @Operation(summary = "Lấy tất cả đơn hàng (Admin)")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getAllOrders(
            @PageableDefault(size = 20) Pageable pageable) {
        Page<OrderResponse> page = orderService.getAllOrders(pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(page)));
    }

    @Operation(summary = "Cập nhật trạng thái đơn hàng (Admin)")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(orderService.updateOrderStatus(id, request)));
    }

    @Operation(summary = "Hủy đơn hàng")
    @DeleteMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.cancelOrder(id)));
    }
}
