package com.core.beautyshop.controller;

import io.swagger.v3.oas.annotations.tags.Tag;


import com.core.beautyshop.dto.common.ApiResponse;
import com.core.beautyshop.dto.request.AddToCartRequest;
import com.core.beautyshop.dto.response.CartResponse;
import com.core.beautyshop.service.cart.CartService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Quản lý giỏ hàng", description = "API quản lý giỏ hàng")
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String sessionId,
            HttpServletRequest req) {
        CartResponse cart = cartService.getCart(userId, sessionId);
        return ResponseEntity.ok(ApiResponse.success(cart, req.getRequestURI()));
    }

    @PostMapping("/add")
    public ResponseEntity<ApiResponse<CartResponse>> addToCart(
            @RequestParam(required = false) Long userId,
            @Valid @RequestBody AddToCartRequest request,
            HttpServletRequest req) {
        CartResponse updatedCart = cartService.addToCart(userId, request);
        return ResponseEntity.ok(ApiResponse.success(updatedCart, req.getRequestURI()));
    }

    @PutMapping("/{cartId}/items/{itemId}")
    public ResponseEntity<ApiResponse<CartResponse>> updateCartItem(
            @PathVariable Long cartId,
            @PathVariable Long itemId,
            @RequestParam Integer quantity,
            HttpServletRequest req) {
        CartResponse updatedCart = cartService.updateCartItem(cartId, itemId, quantity);
        return ResponseEntity.ok(ApiResponse.success(updatedCart, req.getRequestURI()));
    }

    @DeleteMapping("/{cartId}/items/{itemId}")
    public ResponseEntity<ApiResponse<CartResponse>> removeCartItem(
            @PathVariable Long cartId,
            @PathVariable Long itemId,
            HttpServletRequest req) {
        CartResponse updatedCart = cartService.removeCartItem(cartId, itemId);
        return ResponseEntity.ok(ApiResponse.success(updatedCart, req.getRequestURI()));
    }

    @DeleteMapping("/{cartId}/clear")
    public ResponseEntity<ApiResponse<Void>> clearCart(@PathVariable Long cartId, HttpServletRequest req) {
        cartService.clearCart(cartId);
        return ResponseEntity.ok(ApiResponse.success(null, req.getRequestURI()));
    }
}
