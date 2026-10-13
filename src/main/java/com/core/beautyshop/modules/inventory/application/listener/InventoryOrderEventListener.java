package com.core.beautyshop.modules.inventory.application.listener;

import com.core.beautyshop.modules.inventory.api.InventoryFacade;
import com.core.beautyshop.modules.order.api.event.OrderEvents;
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
        log.info("Handling OrderCancelledEvent for orderId={} with {} items",
                event.getOrderId(), event.getItems() != null ? event.getItems().size() : 0);

        if (event.getItems() != null) {
            for (OrderEvents.OrderItemSummary item : event.getItems()) {
                inventoryFacade.releaseStock(event.getOrderNumber(), item.getVariantId(), item.getQuantity());
            }
        }
    }

    @EventListener
    public void handleOrderPaid(OrderEvents.OrderPaidEvent event) {
        log.info("Handling OrderPaidEvent in Inventory for orderId={}, orderNumber={} with {} items",
                event.getOrderId(), event.getOrderNumber(), event.getItems() != null ? event.getItems().size() : 0);

        if (event.getItems() != null && event.getOrderNumber() != null) {
            for (OrderEvents.OrderItemSummary item : event.getItems()) {
                try {
                    inventoryFacade.deductStock(event.getOrderNumber(), item.getVariantId(), item.getQuantity());
                } catch (Exception e) {
                    log.warn("Stock deduction on OrderPaid skipped or already processed for variant {}: {}",
                            item.getVariantId(), e.getMessage());
                }
            }
        }
    }

    @EventListener
    public void handleOrderDelivered(OrderEvents.OrderDeliveredEvent event) {
        log.info("Handling OrderDeliveredEvent for orderId={} with {} items",
                event.getOrderId(), event.getItems() != null ? event.getItems().size() : 0);

        if (event.getItems() != null) {
            for (OrderEvents.OrderItemSummary item : event.getItems()) {
                try {
                    inventoryFacade.deductStock(event.getOrderNumber(), item.getVariantId(), item.getQuantity());
                } catch (Exception e) {
                    log.warn("Stock deduction on OrderDelivered skipped (likely deducted on payment): {}", e.getMessage());
                }
            }
        }
    }

    @EventListener
    public void handleOrderReturned(OrderEvents.OrderReturnedEvent event) {
        log.info("Handling OrderReturnedEvent for orderId={} with {} items",
                event.getOrderId(), event.getItems() != null ? event.getItems().size() : 0);

        if (event.getItems() != null) {
            for (OrderEvents.OrderItemSummary item : event.getItems()) {
                inventoryFacade.returnStock(event.getOrderNumber(), item.getVariantId(), item.getQuantity());
            }
        }
    }
}
