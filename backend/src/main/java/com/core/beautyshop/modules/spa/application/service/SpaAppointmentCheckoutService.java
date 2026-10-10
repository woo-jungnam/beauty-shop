package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.identity.api.IdentityFacade;
import com.core.beautyshop.modules.order.api.OrderFacade;
import com.core.beautyshop.modules.order.api.dto.*;
import com.core.beautyshop.modules.spa.application.dto.request.*;
import com.core.beautyshop.modules.spa.domain.*;
import com.core.beautyshop.modules.spa.domain.enums.*;
import com.core.beautyshop.shared.exception.*;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class SpaAppointmentCheckoutService {
    private final EntityManager entities;
    private final OrderFacade orders;
    private final IdentityFacade identity;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @com.core.beautyshop.shared.audit.api.annotation.AuditAction(action = "CREATE_SPA_VISIT_INVOICE", resourceType = "APPOINTMENT")
    public SpaVisitInvoiceResult create(Long appointmentId, CreateSpaVisitInvoiceRequest request, String key) {
        Appointment appointment = lock(appointmentId);
        if (appointment.getStatus() != AppointmentStatus.COMPLETED)
            throw new BusinessException("Only a completed Spa appointment can be invoiced");
        if (appointment.getItems() == null || appointment.getItems().isEmpty())
            throw new BusinessException("Appointment has no service execution evidence; reconciliation is required");
        List<SpaVisitCharge> charges = new ArrayList<>();
        for (AppointmentItem item : appointment.getItems()) {
            if (Boolean.TRUE.equals(item.getIsDeleted())) continue;
            if (item.getExecutionStatus() != AppointmentItemExecutionStatus.PERFORMED
                    && item.getExecutionStatus() != AppointmentItemExecutionStatus.SKIPPED)
                throw new BusinessException("Every service must have a performed or skipped outcome; reconcile legacy visits before checkout");
            if (item.getExecutionStatus() != AppointmentItemExecutionStatus.PERFORMED || item.getTicket() != null) continue;
            String name = item.getServiceNameSnapshot();
            if (name == null || name.isBlank()) name = item.getService().getName();
            charges.add(new SpaVisitCharge(item.getId(), item.getService().getId(), name, item.getPrice()));
        }
        var customer = identity.getUserSummaryById(appointment.getUserId());
        SpaVisitInvoiceResult invoice = orders.createSpaVisitOrder(new CreateSpaVisitOrderCommand(appointmentId, appointment.getUserId(), charges,
                request.paymentMethod(), customer.getFullName(), customer.getPhone(), request.notes(), key));
        appointment.setOrderId(invoice.orderId());
        return invoice;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public SpaVisitInvoiceResult get(Long appointmentId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return canManage() ? orders.getSpaVisitInvoiceForStaff(appointmentId) : orders.getSpaVisitInvoiceForUser(appointmentId, userId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public SpaVisitInvoiceResult collectCash(Long appointmentId, SpaCashReceiptRequest request, String receiptKey) {
        Appointment appointment = lock(appointmentId);
        if (appointment.getStatus() != AppointmentStatus.COMPLETED) throw new BusinessException("Spa appointment is not completed");
        return orders.collectSpaVisitCash(appointmentId, request.amount(), receiptKey);
    }

    private Appointment lock(Long id) {
        Appointment appointment = id == null ? null : entities.find(Appointment.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (appointment == null || Boolean.TRUE.equals(appointment.getIsDeleted())) throw new ResourceNotFoundException("Appointment not found");
        entities.refresh(appointment, LockModeType.PESSIMISTIC_WRITE);
        return appointment;
    }
    private boolean canManage() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(role -> Set.of("ROLE_ADMIN", "ROLE_STAFF").contains(role.getAuthority()));
    }
}
