package com.core.beautyshop.modules.order.application.service;

import com.core.beautyshop.modules.order.api.OrderFacade;
import com.core.beautyshop.modules.order.domain.Order;
import com.core.beautyshop.modules.order.domain.OrderRepository;
import com.core.beautyshop.modules.order.domain.enums.OrderStatus;
import com.core.beautyshop.modules.payment.api.PaymentFacade;
import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderCodSettlementServiceTest {
    @Test
    void shipperRemittanceIsCappedAndReplayDoesNotCountTwice() {
        var orders = mock(OrderRepository.class);
        var payments = mock(PaymentFacade.class);
        var facade = mock(OrderFacade.class);
        var events = mock(ApplicationEventPublisher.class);
        var service = new OrderCodSettlementService(orders, payments, facade, events);
        var order = Order.builder().orderNumber("ORD-1").status(OrderStatus.DELIVERED)
                .paymentMethod(PaymentMethod.COD).shipperCodCollected(BigDecimal.valueOf(100)).build();
        when(orders.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        var first = new OrderCodSettlementService.Settlement(BigDecimal.valueOf(60), "R1", "Received");
        when(payments.recordCarrierRemittance("ORD-1", first.amount(), "COD-SETTLED-R1", first.reason(), 9L))
                .thenReturn(true, false);
        var excess = new OrderCodSettlementService.Settlement(BigDecimal.valueOf(41), "R2", "Received");
        when(payments.recordCarrierRemittance("ORD-1", excess.amount(), "COD-SETTLED-R2", excess.reason(), 9L))
                .thenReturn(true);
        var rest = new OrderCodSettlementService.Settlement(BigDecimal.valueOf(40), "R3", "Received after return");
        when(payments.recordCarrierRemittance("ORD-1", rest.amount(), "COD-SETTLED-R3", rest.reason(), 9L))
                .thenReturn(true);
        try (var auth = mockStatic(SecurityUtils.class)) {
            auth.when(SecurityUtils::getCurrentUserId).thenReturn(9L);
            service.confirm(1L, first);
            service.confirm(1L, first);
            assertEquals(BigDecimal.valueOf(40), order.unsettledCod());
            assertThrows(BusinessException.class, () -> service.confirm(1L, excess));
            order.setStatus(OrderStatus.RETURNED);
            service.confirm(1L, rest);
        }
        assertEquals(BigDecimal.valueOf(100), order.getShipperCodRemitted());
        assertEquals(BigDecimal.ZERO, order.unsettledCod());
        verifyNoInteractions(facade, events);
    }
}
