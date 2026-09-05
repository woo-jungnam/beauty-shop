package com.core.beautyshop.modules.cart.api;

import com.core.beautyshop.modules.cart.api.dto.CartResponse;

public interface CartFacade {
    CartResponse getCart(Long userId, String sessionId);
    void clearCart(Long cartId);
    void clearCartByUserIdOrSessionId(Long userId, String sessionId);
    CartResponse mergeCart(String sessionId, Long userId);
}
