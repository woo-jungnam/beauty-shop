package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.spa.api.TicketMovementController.RefundHistory;
import com.core.beautyshop.modules.spa.domain.TicketSessionMovement;
import com.core.beautyshop.shared.dto.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface SpaTicketMovementService {
    List<RefundHistory> getRefunds(Long ticketId);
    PageResponse<TicketSessionMovement> getMovements(Long ticketId, Pageable pageable);
}
