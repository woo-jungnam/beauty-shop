package com.core.beautyshop.modules.cart.application.facade;

import com.core.beautyshop.modules.cart.api.CartFacade;
import com.core.beautyshop.modules.cart.api.dto.CartResponse;
import com.core.beautyshop.modules.cart.application.service.CartService;
import com.core.beautyshop.modules.cart.domain.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CartFacadeImpl implements CartFacade {

    private final CartService cartService;
    private final CartRepository cartRepository;

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
        if (userId != null) {
            cartRepository.findByUserId(userId).ifPresent(cart -> cartService.clearCart(cart.getId()));
        } else if (sessionId != null) {
            cartRepository.findBySessionId(sessionId).ifPresent(cart -> cartService.clearCart(cart.getId()));
        }
    }

    @Override
    public CartResponse mergeCart(String sessionId, Long userId) {
        return cartService.mergeCart(sessionId, userId);
    }
}
