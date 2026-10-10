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

    @Operation(summary = "Lấy giỏ hàng hiện tại", description = "Tài khoản đăng nhập dùng giỏ của chính mình; khách vãng lai dùng query sessionId. data có thể null nếu chưa có giỏ. Giá hiện tại chỉ là tham khảo; tồn kho được giữ khi checkout.")
    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart(
            @Parameter(description = "Session ID khách vãng lai (nếu chưa đăng nhập)", example = "guest-session-uuid-12345")
            @RequestParam(required = false) String sessionId) {
        CartResponse cart = cartService.getCart(sessionId);
        return ResponseEntity.ok(ApiResponse.success(cart));
    }

    @Operation(summary = "Thêm sản phẩm vào giỏ hàng", description = "Cộng số lượng vào SKU đã có; yêu cầu SKU còn bán và đủ tồn khả dụng cho tổng số lượng. Khách vãng lai phải gửi sessionId trong body; người đăng nhập dùng giỏ của mình. Chưa giữ tồn kho.")
    @PostMapping("/add")
    public ResponseEntity<ApiResponse<CartResponse>> addToCart(
            @Valid @RequestBody AddToCartRequest request) {
        CartResponse updatedCart = cartService.addToCart(request);
        return ResponseEntity.ok(ApiResponse.success(updatedCart));
    }

    @Operation(summary = "Cập nhật số lượng sản phẩm trong giỏ hàng", description = "Thay số lượng của dòng thuộc giỏ chính chủ/đúng phiên khách. quantity≤0 xóa dòng; quantity>0 kiểm tồn khả dụng. quantity và sessionId là query, không phải JSON body.")
    @PutMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<CartResponse>> updateCartItem(
            @Parameter(description = "ID dòng mặt hàng trong giỏ", example = "1") @PathVariable Long itemId,
            @Parameter(description = "Số lượng mới; 0 hoặc âm sẽ xóa dòng", example = "3") @RequestParam Integer quantity,
            @Parameter(description = "Bắt buộc cho giỏ khách vãng lai; phải khớp phiên sở hữu dòng")
            @RequestParam(required = false) String sessionId) {
        CartResponse updatedCart = cartService.updateCartItem(itemId, quantity, sessionId);
        return ResponseEntity.ok(ApiResponse.success(updatedCart));
    }

    @Operation(summary = "Xóa sản phẩm khỏi giỏ hàng", description = "Gỡ bỏ hoàn toàn một dòng sản phẩm khỏi giỏ hàng.")
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<CartResponse>> removeCartItem(
            @Parameter(description = "ID dòng mặt hàng cần xóa", example = "1") @PathVariable Long itemId,
            @Parameter(description = "Bắt buộc cho giỏ khách vãng lai; phải khớp phiên sở hữu dòng")
            @RequestParam(required = false) String sessionId) {
        CartResponse updatedCart = cartService.removeCartItem(itemId, sessionId);
        return ResponseEntity.ok(ApiResponse.success(updatedCart));
    }

    @Operation(summary = "Xóa toàn bộ giỏ hàng", description = "Dọn sạch tất cả sản phẩm trong giỏ hàng hiện tại.")
    @DeleteMapping("/clear")
    public ResponseEntity<ApiResponse<Void>> clearCart(
            @Parameter(description = "Bắt buộc khi chưa đăng nhập; xóa giỏ đúng phiên")
            @RequestParam(required = false) String sessionId) {
        cartService.clearCart(sessionId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Gộp giỏ khách vãng lai vào tài khoản", description = "Yêu cầu JWT. sessionId là query; chuyển/cộng các dòng của giỏ khách vào tài khoản đang đăng nhập rồi xóa dòng giỏ nguồn. Không cho gộp giỏ thuộc tài khoản khác; thao tác không giữ tồn kho.")
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
