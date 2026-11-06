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

    @Test
    void emailDetailIncludesAddressSnapshotsAndPaymentEvidence() {
        var paidAt = java.time.Instant.parse("2026-11-06T03:15:00Z");
        Order order = Order.builder().orderNumber("EMAIL-21").customerName("Nguyễn An").customerPhone("0901234567")
                .shippingAddress("12 Nguyễn Huệ").ward("Bến Nghé").district("Quận 1").city("TP. Hồ Chí Minh")
                .paymentMethod(com.core.beautyshop.shared.domain.enums.PaymentMethod.BANK)
                .paymentStatus(PaymentStatus.PAID).paidAt(paidAt).paidAmount(new BigDecimal("180000"))
                .items(java.util.List.of(com.core.beautyshop.modules.order.domain.OrderItem.builder()
                        .productName("Serum").sku("SERUM-30").imageUrl("/uploads/serum.jpg").quantity(2)
                        .price(new BigDecimal("100000")).discount(new BigDecimal("10000")).build())).build();
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));
        var facade = new OrderFacadeImpl(orderRepository, eventPublisher, paymentFacade, refunds, expirationService, visits, orderRefunds);
        var result = facade.findOrderEmailDetail(21L).orElseThrow();
        assertEquals("12 Nguyễn Huệ, Bến Nghé, Quận 1, TP. Hồ Chí Minh", result.shippingAddress());
        assertEquals(paidAt, result.paidAt());
        assertEquals("PAID", result.paymentStatus());
        assertEquals(new BigDecimal("180000"), result.paidAmount());
        assertEquals("SERUM-30", result.items().getFirst().sku());
        assertEquals("/uploads/serum.jpg", result.items().getFirst().imageUrl());
        order.setAppointmentId(12L);
        order.setShippingAddress("SPA_SERVICE");
        order.setSpaVisitItems(java.util.List.of(com.core.beautyshop.modules.order.domain.SpaVisitInvoiceItem.builder()
                .serviceName("Chăm sóc da").quantity(2).unitPrice(new BigDecimal("90000")).build()));
        var spa = facade.findOrderEmailDetail(21L).orElseThrow();
        assertNull(spa.shippingAddress());
        assertEquals(2, spa.services().getFirst().quantity());
        assertEquals("Chăm sóc da", spa.services().getFirst().serviceName());
    }

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private PaymentFacade paymentFacade;

    @Mock
    private com.core.beautyshop.modules.order.application.service.OrderExpirationService expirationService;
    @Mock private com.core.beautyshop.modules.order.domain.RefundConfirmationRepository refunds;
    @Mock private com.core.beautyshop.modules.order.application.service.SpaVisitOrderService visits;
    @Mock private com.core.beautyshop.modules.order.application.service.OrderRefundService orderRefunds;

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

        OrderFacadeImpl facade = new OrderFacadeImpl(orderRepository, eventPublisher, paymentFacade, refunds, expirationService, visits, orderRefunds);

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
        OrderFacadeImpl facade = new OrderFacadeImpl(orderRepository, eventPublisher, paymentFacade, refunds, expirationService, visits, orderRefunds);

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
        OrderFacadeImpl facade = new OrderFacadeImpl(orderRepository, eventPublisher, paymentFacade, refunds, expirationService, visits, orderRefunds);

        var replay = facade.createSpaPackageOrder(command);

        assertEquals(55L, replay.getOrderId());
        verify(orderRepository, never()).save(any(Order.class));
    }
}
