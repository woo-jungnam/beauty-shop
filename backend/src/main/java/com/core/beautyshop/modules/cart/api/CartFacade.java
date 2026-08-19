package com.core.beautyshop.modules.cart.api;

import com.core.beautyshop.modules.cart.application.dto.response.CartResponse;

public interface CartFacade {
    CartResponse getCart(Long userId, String sessionId);
    void clearCart(Long cartId);
    void clearCartByUserIdOrSessionId(Long userId, String sessionId);
}
