package com.core.beautyshop.modules.cart.api;

import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.modules.cart.application.dto.request.AddToCartRequest;
import com.core.beautyshop.modules.cart.api.dto.CartResponse;
import com.core.beautyshop.modules.cart.application.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    @Operation(summary = "Lấy giỏ hàng hiện tại (theo User đăng nhập hoặc sessionId)")
    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart(
            @RequestParam(required = false) String sessionId) {
        CartResponse cart = cartService.getCart(sessionId);
        return ResponseEntity.ok(ApiResponse.success(cart));
    }

    @Operation(summary = "Thêm sản phẩm vào giỏ hàng")
    @PostMapping("/add")
    public ResponseEntity<ApiResponse<CartResponse>> addToCart(
            @Valid @RequestBody AddToCartRequest request) {
        CartResponse updatedCart = cartService.addToCart(request);
        return ResponseEntity.ok(ApiResponse.success(updatedCart));
    }

    @Operation(summary = "Cập nhật số lượng sản phẩm trong giỏ hàng")
    @PutMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<CartResponse>> updateCartItem(
            @PathVariable Long itemId,
            @RequestParam Integer quantity) {
        CartResponse updatedCart = cartService.updateCartItem(itemId, quantity);
        return ResponseEntity.ok(ApiResponse.success(updatedCart));
    }

    @Operation(summary = "Xóa sản phẩm khỏi giỏ hàng")
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<CartResponse>> removeCartItem(
            @PathVariable Long itemId) {
        CartResponse updatedCart = cartService.removeCartItem(itemId);
        return ResponseEntity.ok(ApiResponse.success(updatedCart));
    }

    @Operation(summary = "Xóa toàn bộ giỏ hàng")
    @DeleteMapping("/clear")
    public ResponseEntity<ApiResponse<Void>> clearCart() {
        cartService.clearCart();
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Gộp giỏ hàng khách vãng lai (sessionId) vào tài khoản khi đăng nhập")
    @PostMapping("/merge")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CartResponse>> mergeCart(@RequestParam String sessionId) {
        Long userId = com.core.beautyshop.shared.security.utils.SecurityUtils.getCurrentUserId();
        CartResponse updatedCart = cartService.mergeCart(sessionId, userId);
        return ResponseEntity.ok(ApiResponse.success(updatedCart, "Gộp giỏ hàng thành công"));
    }
}
