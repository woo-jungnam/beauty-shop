package com.core.beautyshop.modules.order.application.dto.request;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "ADMIN xác nhận chứng từ đã hoàn thủ công; không phát lệnh chuyển tiền. Phải hoàn toàn bộ tiền thực thu còn chưa hoàn")
public record ConfirmRefundRequest(
        @Schema(description = "Mã chứng từ ổn định; cùng mã/order/amount retry không ghi hai lần", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 100, example = "REFUND-20261002-001") @NotBlank @Size(max = 100) String reference,
        @Schema(description = "Dương và bằng paidAmount−refundedAmount; service chỉ nhận scale≤2", requiredMode = Schema.RequiredMode.REQUIRED, example = "301") @NotNull @DecimalMin(value = "0", inclusive = false) java.math.BigDecimal amount,
        @Schema(description = "Bắt buộc không trống cho invoice Spa COMPLETED; lý do ADMIN phê duyệt", maxLength = 250, example = "Quản lý duyệt hoàn toàn bộ theo đề nghị khách") @Size(max = 250) String approvalReason) {
    public ConfirmRefundRequest(String reference, java.math.BigDecimal amount) { this(reference, amount, null); }
}
