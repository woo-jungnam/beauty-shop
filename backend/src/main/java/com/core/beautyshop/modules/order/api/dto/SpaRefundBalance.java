package com.core.beautyshop.modules.order.api.dto;
import java.math.BigDecimal;
public record SpaRefundBalance(Long orderId, Long userId, Long appointmentId, Long packageId,
                               BigDecimal totalAmount, BigDecimal paidAmount, BigDecimal refundedAmount) {}
