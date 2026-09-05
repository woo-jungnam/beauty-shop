package com.core.beautyshop.modules.spa.application.service.impl;

import com.core.beautyshop.modules.identity.api.IdentityFacade;
import com.core.beautyshop.modules.spa.application.dto.request.PurchasePackageRequest;
import com.core.beautyshop.modules.spa.application.dto.response.UserServiceTicketResponse;
import com.core.beautyshop.modules.spa.application.service.SpaTicketService;
import com.core.beautyshop.modules.spa.domain.ServicePackage;
import com.core.beautyshop.modules.spa.domain.ServicePackageRepository;
import com.core.beautyshop.modules.spa.domain.UserServiceTicket;
import com.core.beautyshop.modules.spa.domain.UserServiceTicketRepository;
import com.core.beautyshop.modules.spa.domain.enums.TicketStatus;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpaTicketServiceImpl implements SpaTicketService {

    private final UserServiceTicketRepository ticketRepository;
    private final ServicePackageRepository packageRepository;
    private final IdentityFacade identityFacade;

    @Override
    @Transactional(readOnly = true)
    public List<UserServiceTicketResponse> getMyTickets() {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return ticketRepository.findByUserIdOrderByCreatedAtDesc(currentUserId).stream()
                .map(UserServiceTicketResponse::of)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserServiceTicketResponse> getMyActiveTickets() {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return ticketRepository.findByUserIdAndStatusOrderByCreatedAtDesc(currentUserId, TicketStatus.ACTIVE).stream()
                .filter(ticket -> ticket.getExpiryDate() == null || ticket.getExpiryDate().isAfter(Instant.now()))
                .filter(ticket -> ticket.getUsedSessions() < ticket.getTotalSessions())
                .map(UserServiceTicketResponse::of)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public UserServiceTicketResponse getTicketById(Long id) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        UserServiceTicket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin vé liệu trình với ID: " + id));

        if (!ticket.getUserId().equals(currentUserId) && !SecurityUtils.isAdmin()) {
            throw new AccessDeniedException("Bạn không có quyền xem thông tin vé liệu trình này!");
        }

        return UserServiceTicketResponse.of(ticket);
    }

    @Override
    @Transactional
    public UserServiceTicketResponse purchasePackage(PurchasePackageRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!identityFacade.existsById(currentUserId)) {
            throw new ResourceNotFoundException("Không tìm thấy thông tin người dùng với ID: " + currentUserId);
        }

        ServicePackage servicePackage = packageRepository.findById(request.getPackageId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy gói dịch vụ Spa với ID: " + request.getPackageId()));

        if (Boolean.FALSE.equals(servicePackage.getIsActive())) {
            throw new BusinessException("Gói dịch vụ Spa này hiện đang tạm ngưng phục vụ!");
        }

        int totalSessions = 0;
        if (servicePackage.getItems() != null && !servicePackage.getItems().isEmpty()) {
            totalSessions = servicePackage.getItems().stream()
                    .mapToInt(item -> item.getQuantity() != null ? item.getQuantity() : 1)
                    .sum();
        }
        if (totalSessions <= 0) {
            totalSessions = 1;
        }

        Instant expiryDate = null;
        if (servicePackage.getValidityDays() != null && servicePackage.getValidityDays() > 0) {
            expiryDate = Instant.now().plus(servicePackage.getValidityDays(), ChronoUnit.DAYS);
        }

        UserServiceTicket ticket = UserServiceTicket.builder()
                .userId(currentUserId)
                .servicePackage(servicePackage)
                .totalSessions(totalSessions)
                .usedSessions(0)
                .expiryDate(expiryDate)
                .status(TicketStatus.ACTIVE)
                .build();

        UserServiceTicket saved = ticketRepository.save(ticket);
        log.info("User ID {} purchased Spa Package '{}' (Ticket ID {}, Total Sessions {})",
                currentUserId, servicePackage.getName(), saved.getId(), totalSessions);

        return UserServiceTicketResponse.of(saved);
    }
}
