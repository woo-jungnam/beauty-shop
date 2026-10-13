package com.core.beautyshop.modules.order.application.service;

import com.core.beautyshop.modules.order.domain.OrderRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OrderExpirationJobTest {
    @Test
    void failingBatchCannotPreventLaterOrdersFromExpiring() {
        var orders = mock(OrderRepository.class); var expiration = mock(OrderExpirationService.class);
        when(orders.findExpiredPaymentIdsAfter(any(), eq(0L), any())).thenReturn(List.of(1L, 100L));
        when(orders.findExpiredPaymentIdsAfter(any(), eq(100L), any())).thenReturn(List.of(101L));
        when(orders.findExpiredPaymentIdsAfter(any(), eq(101L), any())).thenReturn(List.of());
        when(expiration.expire(1L)).thenThrow(new IllegalStateException("Legacy allocation missing"));
        new OrderExpirationJob(orders, expiration).expireOrders();
        verify(expiration).expire(101L);
    }
}
