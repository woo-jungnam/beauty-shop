package com.core.beautyshop.modules.order.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.core.beautyshop.modules.order.api.dto.CreateSpaPackageOrderCommand;
import com.core.beautyshop.modules.order.api.dto.CreateSpaVisitOrderCommand;
import com.core.beautyshop.modules.order.api.dto.SpaPackageOrderResult;
import com.core.beautyshop.modules.order.api.dto.SpaRefundBalance;
import com.core.beautyshop.modules.order.api.dto.SpaVisitInvoiceResult;

public interface OrderFacade {
    SpaRefundBalance lockSpaRefundBalance(Long orderId);
    void confirmSpaServiceRefund(Long orderId, BigDecimal amount, String reference, String reason);
    SpaVisitInvoiceResult applySpaVisitAdvance(Long appointmentId, BigDecimal amount, String reference);
    void confirmTreatmentRefund(Long orderId, BigDecimal amount, String reference, String reason);
    void confirmTreatmentRefund(List<Long> orderIds, BigDecimal amount, String reference, String reason);
    BigDecimal getConfirmedRefundAmount(Instant from, Instant to);
    Optional<Instant> findPaidAtForUser(Long orderId, Long userId);
    Optional<String> findPaymentState(String orderNumber);
    boolean markOrderAsPaid(String orderNumber, BigDecimal transferAmount, String referenceCode);
    SpaPackageOrderResult createSpaPackageOrder(CreateSpaPackageOrderCommand command);
    SpaVisitInvoiceResult createSpaVisitOrder(CreateSpaVisitOrderCommand command);
    SpaVisitInvoiceResult getSpaVisitInvoiceForUser(Long appointmentId, Long userId);
    SpaVisitInvoiceResult getSpaVisitInvoiceForStaff(Long appointmentId);
    SpaVisitInvoiceResult collectSpaVisitCash(Long appointmentId, BigDecimal amount, String receiptKey);
    boolean existsById(Long orderId);
    boolean isPaidOrderForUser(Long orderId, Long userId);
    boolean isDeliveredProductPurchase(Long orderId, Long userId, Long productId);
    record OrderRecipientDto(Long userId, String email, String name, String updatedAt) {}
    Optional<OrderRecipientDto> findOrderRecipient(Long orderId);
}
