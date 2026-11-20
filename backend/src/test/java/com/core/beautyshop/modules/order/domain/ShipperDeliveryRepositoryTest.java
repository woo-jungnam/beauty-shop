package com.core.beautyshop.modules.order.domain;

import com.core.beautyshop.modules.order.domain.enums.OrderStatus;
import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@ActiveProfiles("test")
class ShipperDeliveryRepositoryTest {
    @Autowired OrderRepository orders;

    @Test
    void historyAndTotalsUseAssignmentAndDeliveryDatesForTheRequestedShipper() {
        Instant from = Instant.parse("2026-11-19T17:00:00Z");
        Instant to = from.plusSeconds(86400);
        save("DELIVERED", 50L, from.minusSeconds(1), from, BigDecimal.valueOf(80));
        save("ASSIGNED", 50L, from.plusSeconds(1), null, null);
        save("OTHER-SHIPPER", 51L, from, from, BigDecimal.valueOf(100));
        save("NEXT-DAY", 50L, from, to, BigDecimal.valueOf(100));

        var page = PageRequest.of(0, 1);
        var first = orders.findShipperHistory(50L, from, to, null, page);
        assertEquals(2, first.getTotalElements());
        assertEquals("ASSIGNED", first.getContent().getFirst().getOrderNumber());
        assertEquals(1, orders.findShipperHistory(50L, from, to, OrderStatus.DELIVERED, page).getTotalElements());
        assertEquals(1, orders.findShipperDeliveryTimes(50L, from, to).size());

        var totals = orders.getShipperDeliveryTotals(50L, from, to);
        assertEquals(2, totals.getTotalOrders());
        assertEquals(1, totals.getDeliveredOrders());
        assertEquals(0, totals.getTotalCodCollected().compareTo(BigDecimal.valueOf(80)));
        assertEquals(0, totals.getTotalRevenue().compareTo(BigDecimal.valueOf(100)));
        assertEquals(0, totals.getTotalDeliveryFees().compareTo(BigDecimal.TEN));
    }

    private void save(String number, long shipper, Instant assigned, Instant delivered, BigDecimal cod) {
        Order order = Order.builder().orderNumber(number).shipperId(shipper).shipperAssignedAt(assigned)
                .deliveredAt(delivered).shipperCodCollected(cod)
                .status(delivered == null ? OrderStatus.SHIPPED : OrderStatus.DELIVERED)
                .paymentMethod(PaymentMethod.COD).customerName("Customer").customerPhone("0900000000")
                .shippingAddress("Test address").subTotal(BigDecimal.valueOf(90)).shippingFee(BigDecimal.TEN)
                .totalAmount(BigDecimal.valueOf(100)).build();
        order.setCreatedAt(assigned.minusSeconds(3600));
        order.setUpdatedAt(assigned);
        orders.saveAndFlush(order);
    }
}
