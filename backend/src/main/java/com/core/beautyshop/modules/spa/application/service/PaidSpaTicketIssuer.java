package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.identity.api.IdentityFacade;
import com.core.beautyshop.modules.order.api.OrderFacade;
import com.core.beautyshop.modules.spa.api.event.SpaPackagePaidEvent;
import com.core.beautyshop.modules.spa.domain.ServicePackage;
import com.core.beautyshop.modules.spa.domain.ServicePackageRepository;
import com.core.beautyshop.modules.spa.domain.UserServiceTicket;
import com.core.beautyshop.modules.spa.domain.UserServiceTicketRepository;
import com.core.beautyshop.modules.spa.domain.enums.TicketStatus;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaidSpaTicketIssuer {

    private final UserServiceTicketRepository ticketRepository;
    private final ServicePackageRepository packageRepository;
    private final IdentityFacade identityFacade;
    private final OrderFacade orderFacade;
    private final com.core.beautyshop.modules.spa.domain.SpaPurchaseSnapshotRepository snapshots;

    @Transactional
    public void issue(SpaPackagePaidEvent event) {
        validateEvent(event);

        if (ticketRepository.existsByOrderId(event.orderId())) {
            log.info("Spa ticket for paid order {} already exists; skipping duplicate event", event.orderId());
            return;
        }
        if (!identityFacade.existsById(event.userId())) {
            throw new ResourceNotFoundException("Không tìm thấy người dùng với ID: " + event.userId());
        }
        if (!orderFacade.isPaidOrderForUser(event.orderId(), event.userId())) {
            throw new BusinessException("Không thể cấp vé vì đơn hàng chưa được thanh toán hợp lệ");
        }
        var snapshot = snapshots.findById(event.orderId())
                .orElseThrow(() -> new BusinessException("Purchase snapshot missing; reconcile legacy Spa order"));
        if (!snapshot.getPackageId().equals(event.packageId()) || snapshot.getEntitlements().isEmpty()) {
            throw new BusinessException("Invalid Spa purchase snapshot");
        }
        ServicePackage servicePackage = packageRepository.getReferenceById(snapshot.getPackageId());
        int totalSessions = snapshot.getEntitlements().values().stream().reduce(0, Math::addExact);
        Instant expiryDate = snapshot.getValidityDays() != null && snapshot.getValidityDays() > 0
                ? Instant.now().plus(snapshot.getValidityDays(), ChronoUnit.DAYS) : null;

        UserServiceTicket ticket = UserServiceTicket.builder()
                .userId(event.userId())
                .servicePackage(servicePackage)
                .orderId(event.orderId())
                .totalSessions(totalSessions)
                .usedSessions(0)
                .expiryDate(expiryDate)
                .status(TicketStatus.ACTIVE)
                .build();
        snapshot.getEntitlements().forEach((serviceId, quantity) -> ticket.getEntitlements().put(serviceId,
                new com.core.beautyshop.modules.spa.domain.TicketEntitlement(quantity, 0)));
        ticketRepository.save(ticket);

        log.info("Issued Spa ticket for paid order {}, user {}, package {}",
                event.orderId(), event.userId(), event.packageId());
    }

    private void validateEvent(SpaPackagePaidEvent event) {
        if (event == null || event.orderId() == null || event.userId() == null || event.packageId() == null) {
            throw new BusinessException("Sự kiện thanh toán gói Spa không hợp lệ");
        }
    }
}
