package com.core.beautyshop.modules.order.application.service;

import com.core.beautyshop.modules.order.api.dto.*;
import com.core.beautyshop.modules.order.domain.*;
import com.core.beautyshop.modules.order.domain.enums.*;
import com.core.beautyshop.modules.payment.api.PaymentFacade;
import com.core.beautyshop.modules.payment.api.dto.*;
import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import com.core.beautyshop.shared.exception.*;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service @RequiredArgsConstructor
public class SpaVisitOrderService {
    private final OrderRepository orders;
    private final PaymentFacade payments;
    private final jakarta.persistence.EntityManager entities;

    /** Caller holds the appointment write lock for the lifetime of this transaction. */
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public SpaVisitInvoiceResult create(CreateSpaVisitOrderCommand command) {
        validate(command);
        String key = digest("spa-visit:" + command.appointmentId() + ":" + command.idempotencyKey());
        String hash = digest(command.appointmentId() + "\u0000" + command.userId() + "\u0000" + command.paymentMethod()
                + "\u0000" + Objects.toString(command.notes(), "") + "\u0000" + command.items().stream()
                .sorted(Comparator.comparing(SpaVisitCharge::appointmentItemId)).map(Object::toString).toList());
        var previous = orders.findByAppointmentId(command.appointmentId());
        if (previous.isPresent()) {
            Order invoice = previous.get();
            if (!command.userId().equals(invoice.getUserId()) || invoice.getPaymentMethod() != command.paymentMethod()
                    || (key.equals(invoice.getCheckoutKey()) && !hash.equals(invoice.getCheckoutHash()))) {
                throw new BusinessException("The completed visit already has a different immutable invoice");
            }
            return result(invoice);
        }
        BigDecimal subtotal = command.items().stream().map(SpaVisitCharge::unitPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = subtotal.setScale(0, RoundingMode.HALF_UP);
        if (total.compareTo(new BigDecimal("9999999999")) > 0) throw new BusinessException("Invoice amount exceeds the supported limit");
        Order invoice = Order.builder().orderNumber("ORD-" + UUID.randomUUID().toString().replace("-", "").toUpperCase())
                .appointmentId(command.appointmentId()).userId(command.userId()).checkoutKey(key).checkoutHash(hash)
                .customerName(text(command.customerName(), "Spa customer")).customerPhone(text(command.customerPhone(), "N/A"))
                .shippingAddress("SPA_VISIT").paymentMethod(command.paymentMethod()).status(OrderStatus.COMPLETED)
                .paymentStatus(total.signum() == 0 ? PaymentStatus.PAID : PaymentStatus.PENDING)
                .paidAt(total.signum() == 0 ? Instant.now().truncatedTo(ChronoUnit.MICROS) : null)
                .subTotal(subtotal).totalAmount(total).shippingFee(BigDecimal.ZERO).discountAmount(BigDecimal.ZERO)
                .paidAmount(BigDecimal.ZERO).notes(command.notes()).items(new ArrayList<>())
                .spaVisitItems(new ArrayList<>()).statusHistories(new ArrayList<>()).build();
        for (SpaVisitCharge item : command.items()) invoice.getSpaVisitItems().add(SpaVisitInvoiceItem.builder().order(invoice)
                .appointmentItemId(item.appointmentItemId()).serviceId(item.serviceId()).serviceName(item.serviceName())
                .quantity(1).unitPrice(item.unitPrice()).build());
        invoice.getStatusHistories().add(OrderStatusHistory.builder().order(invoice).status(OrderStatus.COMPLETED)
                .notes("Completed Spa visit invoice created; only performed services without tickets are charged").build());
        orders.saveAndFlush(invoice);
        return result(invoice);
    }

    @Transactional(readOnly = true)
    public SpaVisitInvoiceResult forUser(Long appointmentId, Long userId) {
        Order invoice = find(appointmentId);
        if (userId == null || !userId.equals(invoice.getUserId())) throw new AccessDeniedException("You cannot read this Spa invoice");
        return result(invoice);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public SpaVisitInvoiceResult forStaff(Long appointmentId) { return result(find(appointmentId)); }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @com.core.beautyshop.shared.audit.api.annotation.AuditAction(action = "COLLECT_SPA_VISIT_CASH", resourceType = "APPOINTMENT")
    public SpaVisitInvoiceResult collectCash(Long appointmentId, BigDecimal amount, String receiptKey) {
        Long id = orders.findIdByAppointmentId(appointmentId).orElseThrow(() -> new ResourceNotFoundException("Spa invoice not found"));
        Order invoice = orders.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Spa invoice not found"));
        entities.refresh(invoice, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (Boolean.TRUE.equals(invoice.getIsDeleted()) || invoice.getAppointmentId() == null || invoice.getStatus() != OrderStatus.COMPLETED)
            throw new BusinessException("Only a completed Spa visit invoice can receive cash");
        boolean created = payments.recordCashCollection(invoice.getOrderNumber(), amount, receiptKey, SecurityUtils.getCurrentUserId());
        if (!created) return result(invoice);
        if (invoice.getRefundedAmount().signum() > 0 || invoice.getPaymentStatus() == PaymentStatus.REFUNDED
                || amount.compareTo(invoice.getTotalAmount().subtract(invoice.getPaidAmount()).max(BigDecimal.ZERO)) > 0) {
            throw new BusinessException("Cash collection exceeds the outstanding invoice balance or the invoice was refunded");
        }
        invoice.setPaidAmount(invoice.getPaidAmount().add(amount));
        if (invoice.getPaidAmount().compareTo(invoice.getTotalAmount()) >= 0) {
            invoice.setPaymentStatus(PaymentStatus.PAID);
            if (invoice.getPaidAt() == null) invoice.setPaidAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        }
        invoice.getStatusHistories().add(OrderStatusHistory.builder().order(invoice).status(OrderStatus.COMPLETED)
                .notes("Cash receipt recorded; amount=" + amount + "; cashier=" + SecurityUtils.getCurrentUserId()).build());
        orders.save(invoice);
        return result(invoice);
    }

    private Order find(Long appointmentId) {
        return orders.findByAppointmentId(appointmentId).filter(order -> !Boolean.TRUE.equals(order.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("This appointment has no invoice"));
    }

    private SpaVisitInvoiceResult result(Order invoice) {
        BigDecimal due = invoice.getTotalAmount().subtract(invoice.getPaidAmount()).max(BigDecimal.ZERO);
        PaymentInstruction instruction = null;
        if (due.signum() > 0 && invoice.getRefundedAmount().signum() == 0) {
            instruction = invoice.getPaymentMethod() == PaymentMethod.BANK
                    ? payments.processPayment(PaymentMethod.BANK, PaymentOrderDto.builder().orderNumber(invoice.getOrderNumber())
                        .totalAmount(due).customerName(invoice.getCustomerName()).build())
                    : PaymentInstruction.builder().method("CASH").instructionMessage("Thanh toán " + due + " VND tại quầy Spa.").build();
        }
        return new SpaVisitInvoiceResult(invoice.getId(), invoice.getOrderNumber(), invoice.getAppointmentId(), invoice.getUserId(),
                invoice.getStatus(), invoice.getPaymentMethod(), invoice.getPaymentStatus(), invoice.getSubTotal(), invoice.getTotalAmount(),
                invoice.getPaidAmount(), invoice.getRefundedAmount(), due, invoice.getPaidAt(), invoice.getCreatedAt(), invoice.getNotes(),
                invoice.getSpaVisitItems().stream().sorted(Comparator.comparing(SpaVisitInvoiceItem::getAppointmentItemId))
                        .map(item -> new SpaVisitCharge(item.getAppointmentItemId(), item.getServiceId(), item.getServiceName(), item.getUnitPrice())).toList(), instruction);
    }

    private void validate(CreateSpaVisitOrderCommand command) {
        if (command == null || command.appointmentId() == null || command.userId() == null || command.items() == null
                || (command.paymentMethod() != PaymentMethod.BANK && command.paymentMethod() != PaymentMethod.CASH)
                || command.idempotencyKey() == null || command.idempotencyKey().isBlank() || command.idempotencyKey().length() > 128
                || (command.notes() != null && command.notes().length() > 500)) throw new BusinessException("Invalid Spa visit checkout");
        Set<Long> ids = new HashSet<>();
        for (SpaVisitCharge item : command.items()) {
            if (item == null || item.appointmentItemId() == null || item.serviceId() == null || !ids.add(item.appointmentItemId())
                    || item.serviceName() == null || item.serviceName().isBlank() || item.serviceName().length() > 255
                    || item.unitPrice() == null || item.unitPrice().signum() < 0 || item.unitPrice().scale() > 2)
                throw new BusinessException("Invalid performed-service snapshot");
        }
    }
    private String text(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private String digest(String value) {
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
}
