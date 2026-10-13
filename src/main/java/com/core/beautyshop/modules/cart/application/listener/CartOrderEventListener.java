package com.core.beautyshop.modules.cart.application.listener;

import com.core.beautyshop.modules.cart.api.CartFacade;
import com.core.beautyshop.modules.order.api.event.OrderEvents;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CartOrderEventListener {
    private final CartFacade cartFacade;

    @EventListener
    public void handleOrderCreated(OrderEvents.OrderCreatedEvent event) {
        cartFacade.clearCartByUserIdOrSessionId(event.getUserId(), event.getSessionId());
    }
}
