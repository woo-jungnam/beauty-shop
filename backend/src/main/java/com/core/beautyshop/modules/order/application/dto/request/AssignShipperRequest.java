package com.core.beautyshop.modules.order.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu điều phối tài xế bưu tá cho đơn hàng")
public class AssignShipperRequest {
    @NotNull
    @Positive
    @Schema(description = "ID tài khoản tài xế")
    private Long shipperId;

    @Size(max = 400)
    @Schema(description = "Ghi chú điều phối", example = "Giao hỏa tốc trước 17h")
    private String notes;
}

