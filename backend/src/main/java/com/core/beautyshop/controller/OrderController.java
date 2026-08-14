package com.core.beautyshop.controller;

import io.swagger.v3.oas.annotations.tags.Tag;


import com.core.beautyshop.dto.common.ApiResponse;
import com.core.beautyshop.dto.request.CheckoutRequest;
import com.core.beautyshop.dto.response.OrderResponse;
import com.core.beautyshop.service.order.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Quản lý đơn hàng", description = "API quản lý đơn hàng")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<OrderResponse>> checkout(
            @RequestParam(required = false) Long userId,
            @Valid @RequestBody CheckoutRequest request,
            HttpServletRequest req) {
        OrderResponse order = orderService.checkout(userId, request);
        return ResponseEntity.ok(ApiResponse.created(order, "Order created successfully", req.getRequestURI()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(@PathVariable Long id, HttpServletRequest req) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrderById(id), req.getRequestURI()));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<OrderResponse>>> getOrdersByUser(
            @PathVariable Long userId,
            org.springframework.data.domain.Pageable pageable,
            HttpServletRequest req) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrdersByUser(userId, pageable), req.getRequestURI()));
    }

    @GetMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<OrderResponse>>> getAllOrders(
            org.springframework.data.domain.Pageable pageable,
            HttpServletRequest req) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getAllOrders(pageable), req.getRequestURI()));
    }

    @PutMapping("/{id}/status")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable Long id,
            @Valid @RequestBody com.core.beautyshop.dto.request.UpdateOrderStatusRequest request,
            HttpServletRequest req) {
        return ResponseEntity.ok(ApiResponse.success(orderService.updateOrderStatus(id, request), req.getRequestURI()));
    }

    @DeleteMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @PathVariable Long id,
            @RequestParam(required = false) Long userId,
            HttpServletRequest req) {
        return ResponseEntity.ok(ApiResponse.success(orderService.cancelOrder(id, userId), req.getRequestURI()));
    }
}
