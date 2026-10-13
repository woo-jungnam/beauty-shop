package com.core.beautyshop.modules.order.api.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class CreateSpaPackageOrderCommand {
    Long userId;
    Long servicePackageId;
    String packageName;
    BigDecimal amount;
    String customerName;
    String customerPhone;
    String notes;
    String idempotencyKey;
}
