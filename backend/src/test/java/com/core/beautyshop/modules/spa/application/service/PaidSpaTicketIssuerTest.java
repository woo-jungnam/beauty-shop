package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.identity.api.IdentityFacade;
import com.core.beautyshop.modules.order.api.OrderFacade;
import com.core.beautyshop.modules.spa.api.event.SpaPackagePaidEvent;
import com.core.beautyshop.modules.spa.domain.ServicePackage;
import com.core.beautyshop.modules.spa.domain.ServicePackageItem;
import com.core.beautyshop.modules.spa.domain.ServicePackageRepository;
import com.core.beautyshop.modules.spa.domain.UserServiceTicket;
import com.core.beautyshop.modules.spa.domain.UserServiceTicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaidSpaTicketIssuerTest {

    @Mock private UserServiceTicketRepository ticketRepository;
    @Mock private ServicePackageRepository packageRepository;
    @Mock private IdentityFacade identityFacade;
    @Mock private OrderFacade orderFacade;
    @Mock private com.core.beautyshop.modules.spa.domain.SpaPurchaseSnapshotRepository snapshots;
    @InjectMocks private PaidSpaTicketIssuer issuer;

    @Test
    void issuesTicketOnlyFromPaidEventAndLinksOrder() {
        ServicePackage servicePackage = activePackage();
        when(packageRepository.getReferenceById(30L)).thenReturn(servicePackage);
        var snapshot = new com.core.beautyshop.modules.spa.domain.SpaPurchaseSnapshot();
        snapshot.setOrderId(10L); snapshot.setPackageId(30L); snapshot.setValidityDays(30);
        snapshot.getEntitlements().put(1L, 2); snapshot.getEntitlements().put(2L, 3);
        when(snapshots.findById(10L)).thenReturn(Optional.of(snapshot));
        when(ticketRepository.existsByOrderId(10L)).thenReturn(false);
        when(identityFacade.existsById(20L)).thenReturn(true);
        when(orderFacade.isPaidOrderForUser(10L, 20L)).thenReturn(true);
        Instant paidAt = Instant.now().minus(7, ChronoUnit.DAYS);
        when(orderFacade.findPaidAtForUser(10L, 20L)).thenReturn(Optional.of(paidAt));

        issuer.issue(new SpaPackagePaidEvent(10L, 20L, 30L));

        ArgumentCaptor<UserServiceTicket> captor = ArgumentCaptor.forClass(UserServiceTicket.class);
        verify(ticketRepository).save(captor.capture());
        UserServiceTicket ticket = captor.getValue();
        assertEquals(10L, ticket.getOrderId());
        assertEquals(20L, ticket.getUserId());
        assertEquals(5, ticket.getTotalSessions());
        assertNotNull(ticket.getExpiryDate());
        assertEquals(paidAt.plus(30, ChronoUnit.DAYS), ticket.getExpiryDate());
    }

    @Test
    void duplicatePaidEventDoesNotIssueAnotherTicket() {
        when(ticketRepository.existsByOrderId(10L)).thenReturn(true);

        issuer.issue(new SpaPackagePaidEvent(10L, 20L, 30L));

        verify(ticketRepository, never()).save(any());
        verify(identityFacade, never()).existsById(any());
        verify(orderFacade, never()).isPaidOrderForUser(any(), any());
    }

    @Test
    void rejectsEventWhenOrderIsNotPaidForUser() {
        when(ticketRepository.existsByOrderId(10L)).thenReturn(false);
        when(identityFacade.existsById(20L)).thenReturn(true);
        when(orderFacade.isPaidOrderForUser(10L, 20L)).thenReturn(false);

        assertThrows(com.core.beautyshop.shared.exception.BusinessException.class,
                () -> issuer.issue(new SpaPackagePaidEvent(10L, 20L, 30L)));

        verify(ticketRepository, never()).save(any());
    }

    private ServicePackage activePackage() {
        return ServicePackage.builder()
                .name("Paid package")
                .isActive(true)
                .validityDays(30)
                .items(List.of(
                        ServicePackageItem.builder().quantity(2).build(),
                        ServicePackageItem.builder().quantity(3).build()))
                .build();
    }

    @Test
    void delayedIssuanceCannotMakeAnAlreadyExpiredPurchaseActive() {
        var snapshot = new com.core.beautyshop.modules.spa.domain.SpaPurchaseSnapshot();
        snapshot.setOrderId(10L); snapshot.setPackageId(30L); snapshot.setValidityDays(30);
        snapshot.getEntitlements().put(1L, 2);
        when(snapshots.findById(10L)).thenReturn(Optional.of(snapshot));
        when(packageRepository.getReferenceById(30L)).thenReturn(activePackage());
        when(identityFacade.existsById(20L)).thenReturn(true);
        when(orderFacade.isPaidOrderForUser(10L, 20L)).thenReturn(true);
        Instant paidAt = Instant.now().minus(45, ChronoUnit.DAYS);
        when(orderFacade.findPaidAtForUser(10L, 20L)).thenReturn(Optional.of(paidAt));
        issuer.issue(new SpaPackagePaidEvent(10L, 20L, 30L));
        var captor = ArgumentCaptor.forClass(UserServiceTicket.class);
        verify(ticketRepository).save(captor.capture());
        assertEquals(paidAt.plus(30, ChronoUnit.DAYS), captor.getValue().getExpiryDate());
        assertEquals(com.core.beautyshop.modules.spa.domain.enums.TicketStatus.EXPIRED, captor.getValue().getStatus());
    }
}
