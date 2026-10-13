package com.core.beautyshop.modules.promotion.application.listener;

import com.core.beautyshop.modules.order.api.event.OrderEvents;
import com.core.beautyshop.modules.promotion.api.PromotionFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PromotionOrderEventListener {

    private final PromotionFacade promotionFacade;

    @EventListener
    public void handleOrderCancelled(OrderEvents.OrderCancelledEvent event) {
        if (event == null || event.getOrderId() == null) return;
        log.info("Handling OrderCancelledEvent in Promotion for orderId={}", event.getOrderId());
        promotionFacade.releaseVoucher(event.getOrderId());
    }
}
