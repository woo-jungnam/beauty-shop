package com.core.beautyshop.modules.inventory.application.listener;

import com.core.beautyshop.modules.inventory.api.InventoryFacade;
import com.core.beautyshop.modules.order.api.event.OrderEvents;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InventoryOrderEventListenerTest {

    @Test
    void returnedOrderRestoresEveryItemToInventory() {
        InventoryFacade inventoryFacade = mock(InventoryFacade.class);
        InventoryOrderEventListener listener = new InventoryOrderEventListener(inventoryFacade);
        OrderEvents.OrderItemSummary item = OrderEvents.OrderItemSummary.builder()
                .variantId(8L)
                .quantity(2)
                .build();

        listener.handleOrderReturned(OrderEvents.OrderReturnedEvent.builder()
                .orderId(1L)
                .orderNumber("ORD-1")
                .items(List.of(item))
                .build());

        verify(inventoryFacade).returnStock("ORD-1", 8L, 2);
    }
}
