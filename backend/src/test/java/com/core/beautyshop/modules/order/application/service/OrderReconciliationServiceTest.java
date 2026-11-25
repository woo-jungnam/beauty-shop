package com.core.beautyshop.modules.order.application.service;

import com.core.beautyshop.modules.order.domain.Order;
import com.core.beautyshop.modules.order.domain.OrderRepository;
import com.core.beautyshop.modules.order.domain.enums.OrderStatus;
import com.core.beautyshop.modules.order.domain.enums.PaymentStatus;
import com.core.beautyshop.modules.payment.api.PaymentFacade;
import com.core.beautyshop.modules.payment.api.dto.CodSettlementTransactionDto;
import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderReconciliationServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void summarizesPendingSettledInTransitByShipperAndEnrichesSettlementHistory() {
        var orderRepository = mock(OrderRepository.class);
        var paymentFacade = mock(PaymentFacade.class);
        var service = new OrderReconciliationService(orderRepository, paymentFacade);

        var shipperPending = Order.builder()
                .orderNumber("ORD-101")
                .customerName("Nguyễn An")
                .status(OrderStatus.DELIVERED)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.PAID)
                .totalAmount(new BigDecimal("350000"))
                .paidAmount(new BigDecimal("350000"))
                .shipperId(50L)
                .shipperName("Trần Văn Tài")
                .shipperPhone("0909123456")
                .shipperCodCollected(new BigDecimal("350000"))
                .shipperCodRemitted(new BigDecimal("100000"))
                .build();
        shipperPending.setId(101L);

        var shipperInTransit = Order.builder()
                .orderNumber("ORD-102")
                .customerName("Lê Bình")
                .status(OrderStatus.SHIPPED)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.PENDING)
                .totalAmount(new BigDecimal("200000"))
                .paidAmount(BigDecimal.ZERO)
                .shipperId(50L)
                .shipperName("Trần Văn Tài")
                .shipperPhone("0909123456")
                .build();
        shipperInTransit.setId(102L);

        var carrierSettled = Order.builder()
                .orderNumber("ORD-103")
                .customerName("Phạm Chi")
                .status(OrderStatus.DELIVERED)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.PAID)
                .totalAmount(new BigDecimal("420000"))
                .paidAmount(new BigDecimal("420000"))
                .carrierName("GHN")
                .build();
        carrierSettled.setId(103L);

        when(orderRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(shipperPending, shipperInTransit, carrierSettled));
        when(orderRepository.count(any(Specification.class))).thenReturn(4L);

        var summary = service.getSummary(null, null, null, null, null);
        assertEquals(1L, summary.pendingCodOrders());
        assertEquals(new BigDecimal("250000"), summary.pendingCodAmount());
        assertEquals(1L, summary.settledCodOrders());
        assertEquals(new BigDecimal("520000"), summary.settledCodAmount());
        assertEquals(1L, summary.inTransitCodOrders());
        assertEquals(new BigDecimal("200000"), summary.inTransitCodAmount());
        assertEquals(new BigDecimal("250000"), summary.shipperPendingCodAmount());
        assertEquals(BigDecimal.ZERO, summary.carrierPendingCodAmount());
        assertEquals(4L, summary.attentionOrdersCount());
        assertEquals(2, summary.byShipper().size());

        var firstChannel = summary.byShipper().getFirst();
        assertEquals(50L, firstChannel.shipperId());
        assertFalse(firstChannel.carrierChannel());
        assertEquals(new BigDecimal("250000"), firstChannel.pendingAmount());
        assertEquals(new BigDecimal("200000"), firstChannel.inTransitAmount());

        var secondChannel = summary.byShipper().get(1);
        assertTrue(secondChannel.carrierChannel());
        assertEquals("Hãng VC: GHN", secondChannel.label());
        assertEquals(new BigDecimal("420000"), secondChannel.remittedAmount());

        var pageable = PageRequest.of(0, 15);
        when(paymentFacade.searchCodSettlements("ORD-101", null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(new CodSettlementTransactionDto(
                        900L, "ORD-101", "COD-SETTLED-R1", new BigDecimal("100000"),
                        "Nộp đợt 1", Instant.parse("2026-11-25T05:00:00Z")
                )), pageable, 1));
        when(orderRepository.findByOrderNumberInAndIsDeletedFalse(List.of("ORD-101")))
                .thenReturn(List.of(shipperPending));

        var history = service.getSettlementHistory("ORD-101", null, null, pageable);
        assertEquals(1, history.getTotalElements());
        var row = history.getContent().getFirst();
        assertEquals(101L, row.orderId());
        assertEquals("Trần Văn Tài", row.shipperName());
        assertEquals("Nguyễn An", row.customerName());
    }
}

