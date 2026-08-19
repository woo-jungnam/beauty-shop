package com.core.beautyshop.modules.cart.application.listener;

import com.core.beautyshop.modules.cart.api.CartFacade;
import com.core.beautyshop.modules.order.domain.event.OrderEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CartOrderEventListener {

    private final CartFacade cartFacade;

    @EventListener
    public void handleOrderCreated(OrderEvents.OrderCreatedEvent event) {
        log.info("OrderCreatedEvent received for orderId={}, clearing cart for userId={}, sessionId={}",
                event.getOrderId(), event.getUserId(), event.getSessionId());
        try {
            cartFacade.clearCartByUserIdOrSessionId(event.getUserId(), event.getSessionId());
        } catch (Exception e) {
            log.error("Failed to clear cart after order creation: {}", e.getMessage(), e);
        }
    }
}
