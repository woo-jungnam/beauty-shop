package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.spa.domain.*;
import com.core.beautyshop.modules.spa.domain.enums.*;
import com.core.beautyshop.shared.exception.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class SpaCatalogIntegrityIntegrationTest {
    @Autowired BeautyServiceRepository services;
    @Autowired ServicePackageRepository packages;
    @Autowired UserServiceTicketRepository tickets;
    @Autowired AppointmentRepository appointments;
    @Autowired AdminSpaCatalogService administration;
    @Autowired BeautyServiceService publicCatalog;
    @Autowired PlatformTransactionManager transactions;
    <T> T tx(Supplier<T> action) { return new TransactionTemplate(transactions).execute(status -> action.get()); }
    BeautyService service() {
        return tx(() -> services.saveAndFlush(BeautyService.builder().name("Facial").slug(UUID.randomUUID().toString())
                .durationMinutes(45).preparationTimeMinutes(15).basePrice(BigDecimal.valueOf(300000)).build()));
    }
    @Test void serviceUpdateEvictsPublicDetailsAndDeletionRemovesPublicVisibility() {
        var service = service();
        assertEquals(0, BigDecimal.valueOf(300000).compareTo(publicCatalog.getServiceById(service.getId()).getBasePrice()));
        administration.saveService(service.getId(), new AdminSpaCatalogService.ServiceCommand(null, "Facial updated", service.getSlug(),
                null, null, BigDecimal.valueOf(400000), 60, 15, null, true));
        assertEquals(0, BigDecimal.valueOf(400000).compareTo(publicCatalog.getServiceById(service.getId()).getBasePrice()));
        administration.deleteService(service.getId());
        assertThrows(ResourceNotFoundException.class, () -> publicCatalog.getServiceById(service.getId()));
    }
    @Test void outstandingTicketRightsPreventRetiringServiceUntilRightsAreResolved() {
        var service = service();
        long ticketId = tx(() -> {
            var pack = packages.save(ServicePackage.builder().name("Package").price(BigDecimal.TEN).build());
            var ticket = UserServiceTicket.builder().servicePackage(pack).userId(500L)
                    .orderId(Math.abs(UUID.randomUUID().getMostSignificantBits())).totalSessions(2).usedSessions(1)
                    .expiryDate(Instant.now().plusSeconds(86400)).build();
            ticket.getEntitlements().put(service.getId(), new TicketEntitlement(2, 1));
            return tickets.saveAndFlush(ticket).getId();
        });
        assertThrows(BusinessException.class, () -> administration.deleteService(service.getId()));
        tx(() -> { var ticket = tickets.findById(ticketId).orElseThrow(); ticket.setStatus(TicketStatus.REVOKED); return null; });
        administration.deleteService(service.getId());
        assertThrows(ResourceNotFoundException.class, () -> publicCatalog.getServiceById(service.getId()));
    }
    @Test void confirmedAppointmentPreventsInactivationAndDeletion() {
        var service = service();
        tx(() -> {
            var appointment = Appointment.builder().userId(500L).appointmentDate(LocalDate.now(SpaTimeRules.ZONE).plusDays(3))
                    .startTime(LocalTime.of(10, 0)).endTime(LocalTime.of(11, 0)).status(AppointmentStatus.CONFIRMED)
                    .items(new ArrayList<>()).build();
            appointment.getItems().add(AppointmentItem.builder().appointment(appointment).service(service)
                    .startTime(appointment.getStartTime()).endTime(appointment.getEndTime()).price(service.getBasePrice()).build());
            appointments.saveAndFlush(appointment); return null;
        });
        assertThrows(BusinessException.class, () -> administration.deleteService(service.getId()));
        assertThrows(BusinessException.class, () -> administration.saveService(service.getId(),
                new AdminSpaCatalogService.ServiceCommand(null, "Facial", service.getSlug(), null, null,
                        service.getBasePrice(), 45, 15, null, false)));
        assertTrue(publicCatalog.getServiceById(service.getId()).getIsActive());
    }
}
