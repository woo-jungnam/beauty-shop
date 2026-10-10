package com.core.beautyshop.modules.spa.application.listener;
import com.core.beautyshop.modules.order.api.event.OrderEvents;
import com.core.beautyshop.modules.spa.domain.UserServiceTicketRepository;
import com.core.beautyshop.modules.spa.domain.enums.TicketStatus;
import com.core.beautyshop.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class SpaOrderCancellationListener {
    private final UserServiceTicketRepository tickets;
    @EventListener
    public void cancel(OrderEvents.OrderCancelledEvent event) {
        tickets.findByOrderIdForUpdate(event.getOrderId()).ifPresent(ticket -> {
            if (ticket.getUsedSessions() > 0 || ticket.getReservedSessions() > 0) {
                throw new BusinessException("Cancel pending appointments and reconcile consumed Spa sessions before refunding this package");
            }
            ticket.setStatus(TicketStatus.REVOKED);
            tickets.save(ticket);
        });
    }
}
