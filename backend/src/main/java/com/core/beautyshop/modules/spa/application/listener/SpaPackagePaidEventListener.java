package com.core.beautyshop.modules.spa.application.listener;

import com.core.beautyshop.modules.spa.api.event.SpaPackagePaidEvent;
import com.core.beautyshop.modules.spa.application.service.PaidSpaTicketIssuer;
import com.core.beautyshop.modules.order.api.event.OrderEvents;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SpaPackagePaidEventListener {

    private final PaidSpaTicketIssuer paidSpaTicketIssuer;
    private final ApplicationEventPublisher eventPublisher;

    @EventListener
    public void handleOrderPaid(OrderEvents.OrderPaidEvent event) {
        if (event.getServicePackageId() != null) {
            eventPublisher.publishEvent(new SpaPackagePaidEvent(
                    event.getOrderId(), event.getUserId(), event.getServicePackageId()));
        }
    }

    @EventListener
    public void handle(SpaPackagePaidEvent event) {
        paidSpaTicketIssuer.issue(event);
    }
}
