package com.core.beautyshop.modules.inventory.application.listener;

import com.core.beautyshop.modules.inventory.api.InventoryFacade;
import com.core.beautyshop.modules.order.api.event.OrderEvents;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class InventoryOrderEventListenerTest {

    @Test void aBankReceiptKeepsStockReservedUntilDispatch() {
        InventoryFacade inventory=mock(InventoryFacade.class);var listener=new InventoryOrderEventListener(inventory);
        listener.handleOrderPaid(OrderEvents.OrderPaidEvent.builder().orderId(1L).orderNumber("ORD-1").items(List.of(OrderEvents.OrderItemSummary.builder().variantId(8L).quantity(2).build())).build());
        verifyNoInteractions(inventory);
    }

    @Test
    void deliveryAssertsShipmentEvidenceWithoutDeductingAgain() {
        InventoryFacade inventory = mock(InventoryFacade.class);
        var listener = new InventoryOrderEventListener(inventory);
        listener.handleOrderDelivered(OrderEvents.OrderDeliveredEvent.builder()
                .orderId(1L).orderNumber("ORD-1")
                .items(List.of(OrderEvents.OrderItemSummary.builder().variantId(8L).quantity(2).build()))
                .build());
        verify(inventory).validateDeliveredAllocation("ORD-1", 8L, 2);
        verifyNoMoreInteractions(inventory);
    }

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
