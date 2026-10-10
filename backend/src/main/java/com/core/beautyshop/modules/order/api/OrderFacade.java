package com.core.beautyshop.modules.order.api;

import java.math.BigDecimal;
import com.core.beautyshop.modules.order.api.dto.CreateSpaPackageOrderCommand;
import com.core.beautyshop.modules.order.api.dto.SpaPackageOrderResult;

public interface OrderFacade {
    BigDecimal getConfirmedRefundAmount(java.time.Instant from, java.time.Instant to);
    java.util.Optional<java.time.Instant> findPaidAtForUser(Long orderId, Long userId);
    java.util.Optional<String> findPaymentState(String orderNumber);
    boolean markOrderAsPaid(String orderNumber, BigDecimal transferAmount, String referenceCode);
    SpaPackageOrderResult createSpaPackageOrder(CreateSpaPackageOrderCommand command);
    com.core.beautyshop.modules.order.api.dto.SpaVisitInvoiceResult createSpaVisitOrder(
            com.core.beautyshop.modules.order.api.dto.CreateSpaVisitOrderCommand command);
    com.core.beautyshop.modules.order.api.dto.SpaVisitInvoiceResult getSpaVisitInvoiceForUser(Long appointmentId, Long userId);
    com.core.beautyshop.modules.order.api.dto.SpaVisitInvoiceResult getSpaVisitInvoiceForStaff(Long appointmentId);
    com.core.beautyshop.modules.order.api.dto.SpaVisitInvoiceResult collectSpaVisitCash(
            Long appointmentId, BigDecimal amount, String receiptKey);
    boolean existsById(Long orderId);
    boolean isPaidOrderForUser(Long orderId, Long userId);
    boolean isDeliveredProductPurchase(Long orderId, Long userId, Long productId);
}
