package com.core.beautyshop.modules.spa.application.dto.request;

import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Chốt invoice buổi COMPLETED, chỉ tính PERFORMED không dùng vé; idempotency key nằm trong header")
public record CreateSpaVisitInvoiceRequest(@Schema(description = "Chỉ BANK hoặc CASH; COD không hỗ trợ cho visit invoice", allowableValues = {"BANK", "CASH"}, example = "BANK") @NotNull PaymentMethod paymentMethod,
        @Schema(description = "Ghi chú được lưu lúc lập invoice", maxLength = 500) @Size(max = 500) String notes) { }
