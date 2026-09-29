package com.core.beautyshop.modules.cart.api;

import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.modules.cart.application.dto.request.AddToCartRequest;
import com.core.beautyshop.modules.cart.api.dto.CartResponse;
import com.core.beautyshop.modules.cart.application.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Giỏ hàng mua sắm", description = "Các API thao tác giỏ hàng, thêm/sửa/xóa sản phẩm và gộp giỏ hàng khách vãng lai")
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @Operation(summary = "Lấy giỏ hàng hiện tại", description = "Lấy giỏ hàng dựa theo token người dùng đăng nhập hoặc sessionId (khách vãng lai).")
    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart(
            @Parameter(description = "Session ID khách vãng lai (nếu chưa đăng nhập)", example = "guest-session-uuid-12345")
            @RequestParam(required = false) String sessionId) {
        CartResponse cart = cartService.getCart(sessionId);
        return ResponseEntity.ok(ApiResponse.success(cart));
    }

    @Operation(summary = "Thêm sản phẩm vào giỏ hàng", description = "Thêm một biến thể SKU với số lượng xác định vào giỏ hàng.")
    @PostMapping("/add")
    public ResponseEntity<ApiResponse<CartResponse>> addToCart(
            @Valid @RequestBody AddToCartRequest request) {
        CartResponse updatedCart = cartService.addToCart(request);
        return ResponseEntity.ok(ApiResponse.success(updatedCart));
    }

    @Operation(summary = "Cập nhật số lượng sản phẩm trong giỏ hàng", description = "Thay đổi số lượng của một mặt hàng cụ thể trong giỏ hàng.")
    @PutMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<CartResponse>> updateCartItem(
            @Parameter(description = "ID dòng mặt hàng trong giỏ", example = "1") @PathVariable Long itemId,
            @Parameter(description = "Số lượng mới", example = "3") @RequestParam Integer quantity,
            @Parameter(description = "Session ID required for guest carts")
            @RequestParam(required = false) String sessionId) {
        CartResponse updatedCart = cartService.updateCartItem(itemId, quantity, sessionId);
        return ResponseEntity.ok(ApiResponse.success(updatedCart));
    }

    @Operation(summary = "Xóa sản phẩm khỏi giỏ hàng", description = "Gỡ bỏ hoàn toàn một dòng sản phẩm khỏi giỏ hàng.")
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<CartResponse>> removeCartItem(
            @Parameter(description = "ID dòng mặt hàng cần xóa", example = "1") @PathVariable Long itemId,
            @Parameter(description = "Session ID required for guest carts")
            @RequestParam(required = false) String sessionId) {
        CartResponse updatedCart = cartService.removeCartItem(itemId, sessionId);
        return ResponseEntity.ok(ApiResponse.success(updatedCart));
    }

    @Operation(summary = "Xóa toàn bộ giỏ hàng", description = "Dọn sạch tất cả sản phẩm trong giỏ hàng hiện tại.")
    @DeleteMapping("/clear")
    public ResponseEntity<ApiResponse<Void>> clearCart(
            @Parameter(description = "Session ID required for guest carts")
            @RequestParam(required = false) String sessionId) {
        cartService.clearCart(sessionId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Gộp giỏ hàng khách vãng lai vào tài khoản khi đăng nhập", description = "Khi khách hàng vãng lai đã thêm đồ vào giỏ rồi mới đăng nhập, Frontend gọi API này để chuyển các món từ sessionId sang tài khoản người dùng.")
    @PostMapping("/merge")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CartResponse>> mergeCart(
            @Parameter(description = "Session ID của khách vãng lai trước khi đăng nhập", example = "guest-session-uuid-12345")
            @RequestParam String sessionId) {
        Long userId = com.core.beautyshop.shared.security.utils.SecurityUtils.getCurrentUserId();
        CartResponse updatedCart = cartService.mergeCart(sessionId, userId);
        return ResponseEntity.ok(ApiResponse.success(updatedCart, "Gộp giỏ hàng thành công"));
    }
}
