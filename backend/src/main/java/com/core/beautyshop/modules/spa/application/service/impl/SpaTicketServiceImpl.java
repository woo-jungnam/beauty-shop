package com.core.beautyshop.modules.spa.application.service.impl;

import com.core.beautyshop.modules.identity.api.IdentityFacade;
import com.core.beautyshop.modules.identity.api.dto.UserSummaryDto;
import com.core.beautyshop.modules.order.api.OrderFacade;
import com.core.beautyshop.modules.order.api.dto.CreateSpaPackageOrderCommand;
import com.core.beautyshop.modules.order.api.dto.SpaPackageOrderResult;
import com.core.beautyshop.modules.spa.application.dto.request.PurchasePackageRequest;
import com.core.beautyshop.modules.spa.application.dto.response.UserServiceTicketResponse;
import com.core.beautyshop.modules.spa.application.service.SpaTicketService;
import com.core.beautyshop.modules.spa.domain.ServicePackage;
import com.core.beautyshop.modules.spa.domain.ServicePackageRepository;
import com.core.beautyshop.modules.spa.domain.UserServiceTicket;
import com.core.beautyshop.modules.spa.domain.UserServiceTicketRepository;
import com.core.beautyshop.modules.spa.domain.enums.TicketStatus;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SpaTicketServiceImpl implements SpaTicketService {

    private final UserServiceTicketRepository ticketRepository;
    private final ServicePackageRepository packageRepository;
    private final IdentityFacade identityFacade;
    private final OrderFacade orderFacade;
    private final com.core.beautyshop.modules.spa.domain.SpaPurchaseSnapshotRepository snapshots;

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
                .filter(ticket -> ticket.getOrderId() != null)
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
    public SpaPackageOrderResult purchasePackage(PurchasePackageRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        UserSummaryDto user = identityFacade.getUserSummaryById(currentUserId);
        ServicePackage servicePackage = packageRepository
                .findByIdForUpdateAndIsDeletedFalse(request.getPackageId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy gói dịch vụ Spa với ID: " + request.getPackageId()));

        if (!Boolean.TRUE.equals(servicePackage.getIsActive())) {
            throw new BusinessException("Gói dịch vụ Spa này hiện đang tạm ngưng phục vụ");
        }
        if (servicePackage.getPrice() == null || servicePackage.getPrice().signum() <= 0) {
            throw new BusinessException("Gói dịch vụ Spa chưa có giá thanh toán hợp lệ");
        }

        SpaPackageOrderResult result = orderFacade.createSpaPackageOrder(CreateSpaPackageOrderCommand.builder()
                .userId(currentUserId)
                .servicePackageId(servicePackage.getId())
                .packageName(servicePackage.getName())
                .amount(servicePackage.getPrice())
                .customerName(user.getFullName())
                .customerPhone(user.getPhone())
                .notes(request.getNotes())
                .idempotencyKey(request.getIdempotencyKey())
                .build());
        if (snapshots.existsById(result.getOrderId())) {
            return result;
        }
        var snapshot = new com.core.beautyshop.modules.spa.domain.SpaPurchaseSnapshot();
        snapshot.setOrderId(result.getOrderId());
        snapshot.setPackageId(servicePackage.getId());
        snapshot.setValidityDays(servicePackage.getValidityDays());
        if (servicePackage.getItems() == null || servicePackage.getItems().isEmpty()) {
            throw new BusinessException("Package contains no services");
        }
        for (var item : servicePackage.getItems()) {
            int quantity = item.getQuantity() == null ? 1 : item.getQuantity();
            if (quantity <= 0 || item.getService() == null) throw new BusinessException("Invalid package entitlement");
            snapshot.getEntitlements().merge(item.getService().getId(), quantity, Math::addExact);
        }
        snapshots.save(snapshot);
        return result;
    }

}
