package com.core.beautyshop.modules.order.application.dto.request;
import jakarta.validation.constraints.*;
public record ConfirmRefundRequest(@NotBlank @Size(max = 100) String reference,
        @NotNull @DecimalMin(value = "0", inclusive = false) java.math.BigDecimal amount) {}
