package com.core.beautyshop.modules.order.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
    @Schema(description = "ID tài khoản tài xế (nếu có)")
    private Long shipperId;

    @NotBlank(message = "Tên tài xế không được để trống")
    @Schema(description = "Tên bưu tá / tài xế phụ trách", example = "Nguyễn Văn Giao")
    private String shipperName;

    @Schema(description = "Số điện thoại tài xế", example = "0901234567")
    private String shipperPhone;

    @Schema(description = "Ghi chú điều phối", example = "Giao hỏa tốc trước 17h")
    private String notes;
}

