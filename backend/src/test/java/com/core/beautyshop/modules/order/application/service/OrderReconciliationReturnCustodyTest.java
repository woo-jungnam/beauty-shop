package com.core.beautyshop.modules.order.application.service;

import com.core.beautyshop.modules.order.domain.Order;
import com.core.beautyshop.modules.order.domain.OrderRepository;
import com.core.beautyshop.modules.order.domain.ProductReturn;
import com.core.beautyshop.modules.order.domain.ProductReturnRepository;
import com.core.beautyshop.modules.order.domain.enums.OrderStatus;
import com.core.beautyshop.modules.payment.api.PaymentFacade;
import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class OrderReconciliationReturnCustodyTest {

    @Test
    @SuppressWarnings("unchecked")
    void summaryAggregatesShipperCodAndReturnCustodyWithoutMixingAmounts() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        PaymentFacade paymentFacade = mock(PaymentFacade.class);
        ProductReturnRepository returnRepository = mock(ProductReturnRepository.class);
        OrderReconciliationService service = new OrderReconciliationService(
                orderRepository,
                paymentFacade,
                returnRepository
        );

        Instant now = Instant.now();

        Order codDelivered = Order.builder()
                .orderNumber("ORD-COD-101")
                .customerName("Nguyễn An")
                .customerPhone("0901000111")
                .status(OrderStatus.DELIVERED)
                .paymentMethod(PaymentMethod.COD)
                .totalAmount(BigDecimal.valueOf(250000))
                .paidAmount(BigDecimal.valueOf(50000))
                .shipperCodCollected(BigDecimal.valueOf(250000))
                .shipperCodRemitted(BigDecimal.valueOf(50000))
                .shipperId(11L)
                .shipperName("Tài xế Minh")
                .shipperPhone("0901112222")
                .build();
        codDelivered.setId(1L);
        codDelivered.setCreatedAt(now);

        Order nonCodReturning = Order.builder()
                .orderNumber("ORD-VNPAY-102")
                .customerName("Trần Bình")
                .customerPhone("0902000222")
                .status(OrderStatus.RETURNING_TO_SENDER)
                .paymentMethod(PaymentMethod.BANK)
                .totalAmount(BigDecimal.valueOf(180000))
                .paidAmount(BigDecimal.valueOf(180000))
                .shipperId(11L)
                .shipperName("Tài xế Minh")
                .shipperPhone("0901112222")
                .build();
        nonCodReturning.setId(2L);
        nonCodReturning.setCreatedAt(now);

        Order paidDeliveredOther = Order.builder()
                .orderNumber("ORD-BANK-103")
                .customerName("Lê Chi")
                .customerPhone("0903000333")
                .status(OrderStatus.DELIVERED)
                .paymentMethod(PaymentMethod.BANK)
                .totalAmount(BigDecimal.valueOf(500000))
                .paidAmount(BigDecimal.valueOf(500000))
                .build();
        paidDeliveredOther.setId(3L);
        paidDeliveredOther.setCreatedAt(now);

        ProductReturn approvedPickup = new ProductReturn();
        approvedPickup.setId(10L);
        approvedPickup.setOrderId(1L);
        approvedPickup.setStatus("APPROVED");
        approvedPickup.setReturnMethod("SHIPPER_PICKUP");
        approvedPickup.setRefundAmount(BigDecimal.valueOf(120000));
        approvedPickup.setCreatedAt(now);

        ProductReturn assignedPickup = new ProductReturn();
        assignedPickup.setId(11L);
        assignedPickup.setOrderId(1L);
        assignedPickup.setStatus("PICKUP_ASSIGNED");
        assignedPickup.setReturnMethod("SHIPPER_PICKUP");
        assignedPickup.setPickupShipperId(11L);
        assignedPickup.setPickupShipperName("Tài xế Minh");
        assignedPickup.setPickupShipperPhone("0901112222");
        assignedPickup.setRefundAmount(BigDecimal.valueOf(90000));
        assignedPickup.setCreatedAt(now);

        ProductReturn pickedUpReturn = new ProductReturn();
        pickedUpReturn.setId(12L);
        pickedUpReturn.setOrderId(3L);
        pickedUpReturn.setStatus("PICKED_UP");
        pickedUpReturn.setReturnMethod("SHIPPER_PICKUP");
        pickedUpReturn.setPickupShipperId(22L);
        pickedUpReturn.setPickupShipperName("Tài xế Hùng");
        pickedUpReturn.setPickupShipperPhone("0903334444");
        pickedUpReturn.setRefundAmount(BigDecimal.valueOf(310000));
        pickedUpReturn.setCreatedAt(now);

        ProductReturn receivedReturn = new ProductReturn();
        receivedReturn.setId(13L);
        receivedReturn.setOrderId(3L);
        receivedReturn.setStatus("RECEIVED");
        receivedReturn.setReturnMethod("SHIPPER_PICKUP");
        receivedReturn.setPickupShipperId(22L);
        receivedReturn.setPickupShipperName("Tài xế Hùng");
        receivedReturn.setPickupShipperPhone("0903334444");
        receivedReturn.setRefundAmount(BigDecimal.valueOf(75000));
        receivedReturn.setCreatedAt(now);

        when(orderRepository.findAll(any(Specification.class))).thenReturn(List.of(codDelivered, nonCodReturning));
        when(orderRepository.count(any(Specification.class))).thenReturn(4L);
        when(returnRepository.findByStatusIn(anyCollection())).thenReturn(List.of(
                approvedPickup, assignedPickup, pickedUpReturn, receivedReturn
        ));
        when(orderRepository.findAllById(Set.of(1L, 3L))).thenReturn(List.of(codDelivered, paidDeliveredOther));

        OrderReconciliationService.OrderReconciliationSummary summary =
                service.getSummary(null, null, null, null, null);

        // COD financial balances must stay strictly isolated from return parcel values
        assertEquals(1L, summary.pendingCodOrders());
        assertEquals(BigDecimal.valueOf(200000), summary.pendingCodAmount());
        assertEquals(BigDecimal.valueOf(50000), summary.settledCodAmount());
        assertEquals(BigDecimal.valueOf(200000), summary.shipperPendingCodAmount());
        assertEquals(0L, summary.inTransitCodOrders());
        assertEquals(4L, summary.attentionOrdersCount());

        // Return custody & lifecycle KPIs
        assertEquals(2L, summary.pendingPickupReturnsCount()); // APPROVED + PICKUP_ASSIGNED
        assertEquals(2L, summary.inCustodyReturnsCount());     // RETURNING_TO_SENDER (1) + PICKED_UP (1)
        assertEquals(BigDecimal.valueOf(490000), summary.inCustodyReturnValue()); // 180k + 310k
        assertEquals(1L, summary.awaitingRefundReturnsCount());
        assertEquals(BigDecimal.valueOf(75000), summary.awaitingRefundReturnAmount());

        assertEquals(2, summary.byShipper().size());
        OrderReconciliationService.ShipperReconciliationSummary minh = summary.byShipper().stream()
                .filter(s -> Long.valueOf(11L).equals(s.shipperId()))
                .findFirst()
                .orElseThrow();
        assertEquals(BigDecimal.valueOf(200000), minh.pendingAmount());
        assertEquals(1L, minh.pendingPickupReturnsCount());
        assertEquals(1L, minh.inCustodyReturnsCount());
        assertEquals(BigDecimal.valueOf(180000), minh.inCustodyReturnAmount());

        OrderReconciliationService.ShipperReconciliationSummary hung = summary.byShipper().stream()
                .filter(s -> Long.valueOf(22L).equals(s.shipperId()))
                .findFirst()
                .orElseThrow();
        assertEquals(BigDecimal.ZERO, hung.pendingAmount());
        assertEquals(0L, hung.pendingPickupReturnsCount());
        assertEquals(1L, hung.inCustodyReturnsCount());
        assertEquals(BigDecimal.valueOf(310000), hung.inCustodyReturnAmount());

        // Shipper filter limits both COD orders and ProductReturns consistently
        when(orderRepository.findAll(any(Specification.class))).thenReturn(List.of());
        OrderReconciliationService.OrderReconciliationSummary hungOnly =
                service.getSummary(null, null, 22L, null, null);
        assertEquals(0L, hungOnly.pendingPickupReturnsCount());
        assertEquals(1L, hungOnly.inCustodyReturnsCount());
        assertEquals(BigDecimal.valueOf(310000), hungOnly.inCustodyReturnValue());
        assertEquals(1L, hungOnly.awaitingRefundReturnsCount());
        assertEquals(1, hungOnly.byShipper().size());
        assertEquals(22L, hungOnly.byShipper().getFirst().shipperId());
    }
}

