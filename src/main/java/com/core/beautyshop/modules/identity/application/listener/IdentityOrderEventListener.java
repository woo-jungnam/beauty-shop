package com.core.beautyshop.modules.identity.application.listener;
import com.core.beautyshop.modules.identity.application.service.UserService;
import com.core.beautyshop.modules.order.api.event.OrderEvents;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;

@Component @RequiredArgsConstructor
public class IdentityOrderEventListener {
    private final UserService userService;
    @EventListener
    public void handleOrderDelivered(OrderEvents.OrderDeliveredEvent event) {
        if (event.getUserId() == null || event.getTotalAmount() == null) return;
        int points = event.getTotalAmount().divideToIntegralValue(BigDecimal.valueOf(10000)).intValueExact();
        if (points > 0) userService.addLoyaltyPoints(event.getUserId(), event.getOrderId(), points);
    }
    @EventListener
    public void handleOrderReturned(OrderEvents.OrderReturnedEvent event) {
        if (event.getUserId() != null) userService.reverseLoyaltyPoints(event.getUserId(), event.getOrderId());
    }
}
