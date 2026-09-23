package com.core.beautyshop.modules.spa.application.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PurchasePackageRequest {
    @NotNull(message = "Mã gói dịch vụ không được để trống")
    private Long packageId;

    private String notes;

    @com.fasterxml.jackson.annotation.JsonIgnore
    private String idempotencyKey;
}
