package com.core.beautyshop.modules.payment.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CodSettlementTransactionDto(
        Long id,
        String orderNumber,
        String referenceCode,
        BigDecimal amount,
        String content,
        Instant createdAt
) {}

