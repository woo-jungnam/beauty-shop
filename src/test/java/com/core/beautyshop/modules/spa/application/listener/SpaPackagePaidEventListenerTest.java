package com.core.beautyshop.modules.spa.application.listener;

import com.core.beautyshop.modules.order.api.event.OrderEvents;
import com.core.beautyshop.modules.spa.api.event.SpaPackagePaidEvent;
import com.core.beautyshop.modules.spa.application.service.PaidSpaTicketIssuer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SpaPackagePaidEventListenerTest {

    @Test
    void paidPackageOrderIsBridgedToTicketIssuanceEvent() {
        PaidSpaTicketIssuer issuer = mock(PaidSpaTicketIssuer.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        SpaPackagePaidEventListener listener = new SpaPackagePaidEventListener(issuer, publisher);

        listener.handleOrderPaid(OrderEvents.OrderPaidEvent.builder()
                .orderId(11L)
                .userId(22L)
                .servicePackageId(33L)
                .build());

        ArgumentCaptor<SpaPackagePaidEvent> event = ArgumentCaptor.forClass(SpaPackagePaidEvent.class);
        verify(publisher).publishEvent(event.capture());
        assertEquals(11L, event.getValue().orderId());
        assertEquals(22L, event.getValue().userId());
        assertEquals(33L, event.getValue().packageId());
    }
}
