package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.spa.api.TicketMovementController.RefundHistory;
import com.core.beautyshop.modules.spa.domain.*;
import com.core.beautyshop.shared.dto.PageResponse;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SpaTicketMovementServiceImpl implements SpaTicketMovementService {
    private final UserServiceTicketRepository tickets;
    private final TicketSessionMovementRepository movements;
    private final SpaAccessService access;
    private final SpaServiceRefundRepository refunds;

    @Override
    @Transactional(readOnly = true)
    public List<RefundHistory> getRefunds(Long ticketId) {
        verifyTicketAccess(ticketId);
        return refunds.findByTicketIdOrderByIdDesc(ticketId).stream()
                .map(r -> new RefundHistory(r.getAmount(), r.getReference(), r.getReason(), r.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TicketSessionMovement> getMovements(Long ticketId, Pageable pageable) {
        verifyTicketAccess(ticketId);
        return PageResponse.of(movements.findByTicketIdOrderByIdDesc(ticketId, pageable));
    }

    private void verifyTicketAccess(Long ticketId) {
        var ticket = tickets.findById(ticketId).filter(t -> !Boolean.TRUE.equals(t.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        if (!ticket.getUserId().equals(SecurityUtils.getCurrentUserId()) && !access.canManageReception()) {
            throw new AccessDeniedException("You cannot view this ticket");
        }
    }
}
