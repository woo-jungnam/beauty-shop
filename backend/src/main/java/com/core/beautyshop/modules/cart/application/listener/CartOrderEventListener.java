package com.core.beautyshop.modules.cart.application.listener;

import com.core.beautyshop.modules.cart.api.CartFacade;
import com.core.beautyshop.modules.order.api.event.OrderEvents;
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
        log.info("Đã nhận sự kiện OrderCreatedEvent cho orderId={}, tiến hành xóa giỏ hàng cho userId={}, sessionId={}",
                event.getOrderId(), event.getUserId(), event.getSessionId());
        try {
            cartFacade.clearCartByUserIdOrSessionId(event.getUserId(), event.getSessionId());
        } catch (Exception e) {
            log.error("Lỗi khi xóa giỏ hàng sau khi tạo đơn hàng: {}", e.getMessage(), e);
        }
    }
}
