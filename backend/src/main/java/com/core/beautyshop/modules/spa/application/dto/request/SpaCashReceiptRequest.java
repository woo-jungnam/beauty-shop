package com.core.beautyshop.modules.spa.application.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Một phiếu thu CASH; khóa phiếu thu bắt buộc nằm trong Idempotency-Key header")
public record SpaCashReceiptRequest(@Schema(description = "VND nguyên, ít nhất 1, tối đa 10 chữ số và không vượt số còn thiếu", minimum = "1", maximum = "9999999999", example = "100000") @NotNull @DecimalMin("1") @Digits(integer = 10, fraction = 0) BigDecimal amount) { }
