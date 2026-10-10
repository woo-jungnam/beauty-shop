package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.order.api.OrderFacade;
import com.core.beautyshop.modules.spa.domain.*;
import com.core.beautyshop.modules.spa.domain.enums.TicketStatus;
import com.core.beautyshop.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpaAdministrationRulesTest {
    @Mock BeautyServiceRepository services;
    @Mock ServiceCategoryRepository categories;
    @Mock ServicePackageRepository packages;
    @Mock UserServiceTicketRepository tickets;
    @Mock OrderFacade orders;
    @Mock AppointmentRepository appointments;
    @Mock SpaPurchaseSnapshotRepository snapshots;
    @Mock TicketSessionMovementRepository movements;
    @InjectMocks AdminSpaCatalogService catalog;
    @InjectMocks AdminSpaTicketService administration;

    @Test void negativePreparationAndExcessiveDurationAreRejected() {
        for (var values : List.of(new int[]{30, -1}, new int[]{720, 15})) {
            var command = new AdminSpaCatalogService.ServiceCommand(null, "Facial", "facial", null, null,
                    BigDecimal.TEN, values[0], values[1], null, true);
            assertThrows(BusinessException.class, () -> catalog.saveService(null, command));
        }
        verifyNoInteractions(services);
    }
    @Test void packageCannotHaveNonPositiveExpiryOrSubDongPrice() {
        var items = List.of(new AdminSpaCatalogService.PackageItemCommand(1L, 1));
        for (int expiry : List.of(0, -1)) {
            assertThrows(BusinessException.class, () -> catalog.savePackage(null,
                    new AdminSpaCatalogService.PackageCommand("Package", null, BigDecimal.TEN, expiry, null, true, items)));
        }
        assertThrows(BusinessException.class, () -> catalog.savePackage(null,
                new AdminSpaCatalogService.PackageCommand("Package", null, new BigDecimal("0.1"), 30, null, true, items)));
        verifyNoInteractions(packages);
    }
    @Test void packageCannotIncludeDeletedService() {
        var service = BeautyService.builder().name("Deleted").build(); service.setIsDeleted(true);
        when(services.findById(1L)).thenReturn(Optional.of(service));
        assertThrows(com.core.beautyshop.shared.exception.ResourceNotFoundException.class, () -> catalog.savePackage(null,
                new AdminSpaCatalogService.PackageCommand("Package", null, BigDecimal.TEN, 30, null, true,
                        List.of(new AdminSpaCatalogService.PackageItemCommand(1L, 1)))));
        verify(packages, never()).save(any());
    }
    @Test void userAndStatusFiltersAreAppliedTogether() {
        var pageable = PageRequest.of(0, 20);
        when(tickets.findByUserIdAndStatusAndIsDeletedFalse(10L, TicketStatus.EXPIRED, pageable)).thenReturn(Page.empty(pageable));
        assertTrue(administration.find(10L, TicketStatus.EXPIRED, pageable).isEmpty());
        verify(tickets, never()).findByUserIdAndIsDeletedFalse(any(), any());
    }
    @Test void revokedOrUnpaidTicketCannotBeReactivated() {
        var ticket = UserServiceTicket.builder().userId(10L).orderId(20L).totalSessions(3).usedSessions(1)
                .status(TicketStatus.REVOKED).build(); ticket.setId(1L);
        when(tickets.findByIdForUpdate(1L)).thenReturn(Optional.of(ticket));
        assertThrows(BusinessException.class, () -> administration.extend(1L, 10));
        assertThrows(BusinessException.class, () -> administration.compensate(1L, 2L, 1));
        ticket.setStatus(TicketStatus.EXPIRED);
        assertThrows(BusinessException.class, () -> administration.extend(1L, 10));
        assertEquals(3, ticket.getTotalSessions());
    }
    @Test void CompensationDoesNotHideExpiredTicket() {
        var ticket = UserServiceTicket.builder().userId(10L).orderId(20L).totalSessions(3).usedSessions(1)
                .expiryDate(Instant.now().minusSeconds(60)).status(TicketStatus.EXPIRED).build(); ticket.setId(1L);
        ticket.getEntitlements().put(2L, new TicketEntitlement(3, 1));
        when(tickets.findByIdForUpdate(1L)).thenReturn(Optional.of(ticket));
        when(orders.isPaidOrderForUser(20L, 10L)).thenReturn(true);
        when(services.findAvailableByIdForUpdate(2L)).thenReturn(Optional.of(BeautyService.builder().name("Facial").build()));
        administration.compensate(1L, 2L, 1);
        assertEquals(TicketStatus.EXPIRED, ticket.getStatus());
        assertEquals(4, ticket.getTotalSessions());
    }
    @Test void extensionCannotShortenUnlimitedTicket() {
        var ticket = UserServiceTicket.builder().userId(10L).orderId(20L).totalSessions(3).usedSessions(1).build(); ticket.setId(1L);
        when(tickets.findByIdForUpdate(1L)).thenReturn(Optional.of(ticket));
        when(orders.isPaidOrderForUser(20L, 10L)).thenReturn(true);
        assertThrows(BusinessException.class, () -> administration.extend(1L, 10));
        assertNull(ticket.getExpiryDate());
    }
    @Test void retiringServiceCannotInvalidateOutstandingPaidRightsOrAppointments() {
        var value = BeautyService.builder().name("Facial").build(); value.setId(1L);
        when(services.findByIdForUpdate(1L)).thenReturn(Optional.of(value));
        when(appointments.hasOutstandingServiceAppointments(1L)).thenReturn(true);
        assertThrows(BusinessException.class, () -> catalog.deleteService(1L));
        assertFalse(value.getIsDeleted());
        when(appointments.hasOutstandingServiceAppointments(1L)).thenReturn(false);
        when(snapshots.countServiceOrderObligations(eq(1L), any())).thenReturn(1L);
        assertThrows(BusinessException.class, () -> catalog.deleteService(1L));
        assertTrue(value.getIsActive());
    }
    @Test void extensionAndCompensationCannotCreateRightsForRetiredService() {
        var ticket = UserServiceTicket.builder().userId(10L).orderId(20L).totalSessions(3).usedSessions(1)
                .expiryDate(Instant.now().minusSeconds(60)).status(TicketStatus.EXPIRED).build(); ticket.setId(1L);
        ticket.getEntitlements().put(2L, new TicketEntitlement(3, 1));
        when(tickets.findByIdForUpdate(1L)).thenReturn(Optional.of(ticket));
        when(orders.isPaidOrderForUser(20L, 10L)).thenReturn(true);
        var expiry = ticket.getExpiryDate();
        assertThrows(BusinessException.class, () -> administration.extend(1L, 30));
        assertEquals(expiry, ticket.getExpiryDate());
        ticket.setUsedSessions(3); ticket.getEntitlements().get(2L).setUsed(3);
        assertThrows(BusinessException.class, () -> administration.compensate(1L, 2L, 1));
        assertEquals(3, ticket.getTotalSessions());
    }
}
