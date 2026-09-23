package com.core.beautyshop.modules.identity.application.listener;

import com.core.beautyshop.modules.identity.application.service.UserService;
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
class IdentityOrderEventListenerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private IdentityOrderEventListener listener;

    private OrderEvents.OrderDeliveredEvent deliveredEvent;

    @BeforeEach
    void setUp() {
        deliveredEvent = OrderEvents.OrderDeliveredEvent.builder()
                .orderId(200L)
                .orderNumber("ORD-87654321")
                .userId(15L)
                .totalAmount(new BigDecimal("250000"))
                .build();
    }

    @Test
    void handleOrderDelivered_AwardsLoyaltyPoints() {
        listener.handleOrderDelivered(deliveredEvent);

        verify(userService).addLoyaltyPoints(15L, 200L, 25);
    }

    @Test
    void handleOrderDelivered_DoesNothing_WhenUserIdOrTotalAmountNull() {
        OrderEvents.OrderDeliveredEvent guestEvent = OrderEvents.OrderDeliveredEvent.builder()
                .orderId(201L)
                .userId(null)
                .totalAmount(new BigDecimal("100000"))
                .build();

        listener.handleOrderDelivered(guestEvent);

        verifyNoInteractions(userService);
    }

    @Test
    void handleOrderDelivered_PropagatesFailureToRollbackOrder() {
        doThrow(new RuntimeException("DB Lock Exception"))
                .when(userService).addLoyaltyPoints(15L, 200L, 25);

        assertThrows(RuntimeException.class, () -> listener.handleOrderDelivered(deliveredEvent));

        verify(userService).addLoyaltyPoints(15L, 200L, 25);
    }
}
