package com.core.beautyshop.modules.spa.application.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Schema(description = "Tạo đơn BANK mua gói cho tài khoản hiện tại; vé chỉ được cấp khi đơn thanh toán thành công")
public class PurchasePackageRequest {
    @NotNull(message = "Mã gói dịch vụ không được để trống")
    @Schema(description = "Gói và các dịch vụ thành phần còn khả dụng", example = "1")
    private Long packageId;

    @Schema(description = "Ghi chú đơn mua gói; tham gia fingerprint của yêu cầu idempotent", example = "Mua gói ba buổi")
    private String notes;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Schema(hidden = true)
    private String idempotencyKey;
}
