package com.core.beautyshop.modules.order.application.facade;

import com.core.beautyshop.modules.order.api.event.OrderEvents;
import com.core.beautyshop.modules.order.domain.Order;
import com.core.beautyshop.modules.order.domain.OrderRepository;
import com.core.beautyshop.modules.order.domain.enums.OrderStatus;
import com.core.beautyshop.modules.order.domain.enums.PaymentStatus;
import com.core.beautyshop.modules.payment.api.PaymentFacade;
import com.core.beautyshop.modules.order.api.dto.CreateSpaPackageOrderCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderFacadeImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private PaymentFacade paymentFacade;

    @Mock
    private com.core.beautyshop.modules.order.application.service.OrderExpirationService expirationService;

    @Test
    void accumulatesPartialPaymentsUntilOrderIsFullyPaid() {
        Order order = Order.builder()
                .orderNumber("ORD-1234ABCD")
                .userId(7L)
                .servicePackageId(9L)
                .totalAmount(new BigDecimal("100.00"))
                .paidAmount(BigDecimal.ZERO)
                .paymentStatus(PaymentStatus.PENDING)
                .status(OrderStatus.PENDING)
                .statusHistories(new ArrayList<>())
                .items(new ArrayList<>())
                .build();

        when(orderRepository.findByOrderNumberForUpdate("ORD-1234ABCD"))
                .thenReturn(Optional.of(order));

        OrderFacadeImpl facade = new OrderFacadeImpl(orderRepository, eventPublisher, paymentFacade, expirationService);

        assertFalse(facade.markOrderAsPaid(
                "ORD-1234ABCD", new BigDecimal("40.00"), "REF-1"));
        assertEquals(0, order.getPaidAmount().compareTo(new BigDecimal("40.00")));
        assertEquals(PaymentStatus.PENDING, order.getPaymentStatus());

        assertTrue(facade.markOrderAsPaid(
                "ORD-1234ABCD", new BigDecimal("60.00"), "REF-2"));
        assertEquals(0, order.getPaidAmount().compareTo(new BigDecimal("100.00")));
        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
        assertEquals(OrderStatus.PROCESSING, order.getStatus());
        verify(eventPublisher).publishEvent(any(OrderEvents.OrderStatusChangedEvent.class));
        verify(eventPublisher).publishEvent(any(OrderEvents.OrderPaidEvent.class));
        verify(orderRepository, times(2)).save(order);
    }

    @Test
    void latePaymentCannotResurrectAnExpiredOrder() {
        Order order = Order.builder()
                .orderNumber("ORD-LATE")
                .totalAmount(new BigDecimal("100.00"))
                .paidAmount(BigDecimal.ZERO)
                .paymentStatus(PaymentStatus.PENDING)
                .paymentDeadline(java.time.Instant.now().minusSeconds(1))
                .status(OrderStatus.PENDING)
                .statusHistories(new ArrayList<>())
                .items(new ArrayList<>())
                .build();
        order.setId(11L);
        when(orderRepository.findByOrderNumberForUpdate("ORD-LATE")).thenReturn(Optional.of(order));
        when(expirationService.expire(11L)).thenReturn(true);
        OrderFacadeImpl facade = new OrderFacadeImpl(orderRepository, eventPublisher, paymentFacade, expirationService);

        assertTrue(facade.markOrderAsPaid("ORD-LATE", new BigDecimal("100.00"), "REF-LATE"));

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(PaymentStatus.REFUND_PENDING, order.getPaymentStatus());
        verify(eventPublisher, never()).publishEvent(any(OrderEvents.OrderPaidEvent.class));
    }

    @Test
    void spaPurchaseIdempotencyKeyReplaysTheExistingOrder() throws Exception {
        CreateSpaPackageOrderCommand command = CreateSpaPackageOrderCommand.builder()
                .userId(7L)
                .servicePackageId(9L)
                .packageName("Package")
                .amount(new BigDecimal("100.00"))
                .customerName("Customer")
                .customerPhone("0900000000")
                .notes("note")
                .idempotencyKey("retry-key")
                .build();
        String hashInput = String.join("\u0000", "7", "9", "100.00", "note");
        String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(hashInput.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        Order previous = Order.builder()
                .orderNumber("ORD-SPA")
                .checkoutHash(hash)
                .servicePackageId(9L)
                .totalAmount(new BigDecimal("100.00"))
                .customerName("Customer")
                .build();
        previous.setId(55L);
        when(orderRepository.findByCheckoutKey(anyString())).thenReturn(Optional.of(previous));
        OrderFacadeImpl facade = new OrderFacadeImpl(orderRepository, eventPublisher, paymentFacade, expirationService);

        var replay = facade.createSpaPackageOrder(command);

        assertEquals(55L, replay.getOrderId());
        verify(orderRepository, never()).save(any(Order.class));
    }
}
