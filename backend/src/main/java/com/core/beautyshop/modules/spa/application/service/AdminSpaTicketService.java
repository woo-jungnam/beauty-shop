package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.spa.application.dto.response.UserServiceTicketResponse;
import com.core.beautyshop.modules.spa.domain.*;
import com.core.beautyshop.modules.spa.domain.enums.TicketStatus;
import com.core.beautyshop.shared.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AdminSpaTicketService {
    private final UserServiceTicketRepository repository;

    @Transactional(readOnly = true)
    public Page<UserServiceTicketResponse> find(Long userId, TicketStatus status, Pageable pageable) {
        Page<UserServiceTicket> page = userId != null ? repository.findByUserIdAndIsDeletedFalse(userId, pageable)
                : status != null ? repository.findByStatusAndIsDeletedFalse(status, pageable)
                : repository.findByIsDeletedFalse(pageable);
        return page.map(UserServiceTicketResponse::of);
    }

    @Transactional
    public UserServiceTicketResponse extend(Long id, int days) {
        if (days <= 0 || days > 3650) throw new BusinessException("Extension days must be between 1 and 3650");
        UserServiceTicket ticket = lock(id);
        Instant base = ticket.getExpiryDate() == null || ticket.getExpiryDate().isBefore(Instant.now()) ? Instant.now() : ticket.getExpiryDate();
        ticket.setExpiryDate(base.plusSeconds(Math.multiplyExact((long) days, 86400L)));
        if (ticket.getUsedSessions() < ticket.getTotalSessions()) ticket.setStatus(TicketStatus.ACTIVE);
        return UserServiceTicketResponse.of(ticket);
    }

    @Transactional
    public UserServiceTicketResponse compensate(Long id, Long serviceId, int sessions) {
        if (serviceId == null || sessions <= 0 || sessions > 100) throw new BusinessException("Invalid compensation sessions");
        UserServiceTicket ticket = lock(id);
        TicketEntitlement entitlement = ticket.getEntitlements().get(serviceId);
        if (entitlement == null) throw new BusinessException("Service is not included in this ticket");
        entitlement.setTotal(Math.addExact(entitlement.getTotal(), sessions));
        ticket.setTotalSessions(Math.addExact(ticket.getTotalSessions(), sessions));
        ticket.setStatus(TicketStatus.ACTIVE);
        return UserServiceTicketResponse.of(ticket);
    }

    private UserServiceTicket lock(Long id) { return repository.findByIdForUpdate(id)
            .filter(value -> !Boolean.TRUE.equals(value.getIsDeleted()))
            .orElseThrow(() -> new ResourceNotFoundException("Spa ticket not found: " + id)); }
}
