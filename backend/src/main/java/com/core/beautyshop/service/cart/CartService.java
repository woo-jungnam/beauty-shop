package com.core.beautyshop.service.cart;

import com.core.beautyshop.dto.request.AddToCartRequest;
import com.core.beautyshop.dto.response.CartResponse;

public interface CartService {
    CartResponse getCart(Long userId, String sessionId);
    CartResponse addToCart(Long userId, AddToCartRequest request);
    CartResponse updateCartItem(Long cartId, Long itemId, Integer quantity);
    CartResponse removeCartItem(Long cartId, Long itemId);
    void clearCart(Long cartId);
}
