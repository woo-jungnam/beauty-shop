package com.core.beautyshop.modules.cart.application.service;

import com.core.beautyshop.modules.cart.application.dto.request.AddToCartRequest;
import com.core.beautyshop.modules.cart.api.dto.CartResponse;

public interface CartService {
    CartResponse getCart(String sessionId);
    CartResponse getCart(Long userId, String sessionId);
    CartResponse lockCart(Long userId, String sessionId);
    CartResponse addToCart(AddToCartRequest request);
    CartResponse addToCart(Long userId, AddToCartRequest request);
    CartResponse updateCartItem(Long itemId, Integer quantity, String sessionId);
    CartResponse updateCartItem(Long cartId, Long itemId, Integer quantity);
    CartResponse removeCartItem(Long itemId, String sessionId);
    CartResponse removeCartItem(Long cartId, Long itemId);
    void clearCart(String sessionId);
    void clearCart(Long cartId);
    void clearCartByUserIdOrSessionId(Long userId, String sessionId);
    CartResponse mergeCart(String sessionId, Long userId);
}
