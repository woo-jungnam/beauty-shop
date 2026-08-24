package com.core.beautyshop.modules.inventory.application.listener;

import com.core.beautyshop.modules.inventory.api.InventoryFacade;
import com.core.beautyshop.modules.order.domain.event.OrderEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryOrderEventListener {

    private final InventoryFacade inventoryFacade;

    @EventListener
    public void handleOrderCancelled(OrderEvents.OrderCancelledEvent event) {
        log.info("OrderCancelledEvent received for orderId={}, releasing stock for {} items",
                event.getOrderId(), event.getItems() != null ? event.getItems().size() : 0);

        if (event.getItems() != null) {
            for (OrderEvents.OrderItemSummary item : event.getItems()) {
                try {
                    inventoryFacade.releaseStock(item.getVariantId(), item.getQuantity());
                } catch (Exception e) {
                    log.error("Failed to release stock for variantId={}: {}", item.getVariantId(), e.getMessage(), e);
                }
            }
        }
    }

    @EventListener
    public void handleOrderDelivered(OrderEvents.OrderDeliveredEvent event) {
        log.info("OrderDeliveredEvent received for orderId={}, deducting stock for {} items",
                event.getOrderId(), event.getItems() != null ? event.getItems().size() : 0);

        if (event.getItems() != null) {
            for (OrderEvents.OrderItemSummary item : event.getItems()) {
                try {
                    inventoryFacade.deductStock(item.getVariantId(), item.getQuantity());
                } catch (Exception e) {
                    log.error("Failed to deduct stock for variantId={}: {}", item.getVariantId(), e.getMessage(), e);
                }
            }
        }
    }
}
