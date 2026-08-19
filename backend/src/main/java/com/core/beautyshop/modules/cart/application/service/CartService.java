package com.core.beautyshop.modules.cart.application.service;

import com.core.beautyshop.modules.cart.application.dto.request.AddToCartRequest;
import com.core.beautyshop.modules.cart.application.dto.response.CartResponse;

public interface CartService {
    CartResponse getCart(String sessionId);
    CartResponse getCart(Long userId, String sessionId);
    CartResponse addToCart(AddToCartRequest request);
    CartResponse addToCart(Long userId, AddToCartRequest request);
    CartResponse updateCartItem(Long itemId, Integer quantity);
    CartResponse updateCartItem(Long cartId, Long itemId, Integer quantity);
    CartResponse removeCartItem(Long itemId);
    CartResponse removeCartItem(Long cartId, Long itemId);
    void clearCart();
    void clearCart(Long cartId);
}

