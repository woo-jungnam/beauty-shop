package com.core.beautyshop.modules.cart.application.facade;

import com.core.beautyshop.modules.cart.api.CartFacade;
import com.core.beautyshop.modules.cart.api.dto.CartResponse;
import com.core.beautyshop.modules.cart.application.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CartFacadeImpl implements CartFacade {

    private final CartService cartService;

    @Override
    public CartResponse lockCart(Long userId, String sessionId) {
        return cartService.lockCart(userId, sessionId);
    }

    @Override
    public CartResponse getCart(Long userId, String sessionId) {
        return cartService.getCart(userId, sessionId);
    }

    @Override
    public void clearCart(Long cartId) {
        cartService.clearCart(cartId);
    }

    @Override
    public void clearCartByUserIdOrSessionId(Long userId, String sessionId) {
        cartService.clearCartByUserIdOrSessionId(userId, sessionId);
    }

    @Override
    public CartResponse mergeCart(String sessionId, Long userId) {
        return cartService.mergeCart(sessionId, userId);
    }
}
