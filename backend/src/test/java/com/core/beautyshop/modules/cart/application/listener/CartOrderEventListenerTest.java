package com.core.beautyshop.modules.cart.application.listener;

import com.core.beautyshop.modules.cart.api.CartFacade;
import com.core.beautyshop.modules.order.api.event.OrderEvents;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartOrderEventListenerTest {

    @Mock
    private CartFacade cartFacade;

    @InjectMocks
    private CartOrderEventListener listener;

    private OrderEvents.OrderCreatedEvent sampleEvent;

    @BeforeEach
    void setUp() {
        sampleEvent = OrderEvents.OrderCreatedEvent.builder()
                .orderId(100L)
                .orderNumber("ORD-12345678")
                .userId(10L)
                .sessionId("session-abc")
                .totalAmount(new BigDecimal("500000"))
                .build();
    }

    @Test
    void handleOrderCreated_DelegatesToCartFacade() {
        listener.handleOrderCreated(sampleEvent);

        verify(cartFacade).clearCartByUserIdOrSessionId(10L, "session-abc");
    }

    @Test
    void handleOrderCreated_PropagatesFailureToRollbackOrder() {
        doThrow(new RuntimeException("DB Connection Timeout"))
                .when(cartFacade).clearCartByUserIdOrSessionId(10L, "session-abc");

        assertThrows(RuntimeException.class, () -> listener.handleOrderCreated(sampleEvent));

        verify(cartFacade).clearCartByUserIdOrSessionId(10L, "session-abc");
    }
}
