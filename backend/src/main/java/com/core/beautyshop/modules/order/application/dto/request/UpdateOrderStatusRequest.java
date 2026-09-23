package com.core.beautyshop.modules.order.application.dto.request;

import com.core.beautyshop.modules.order.domain.enums.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu cập nhật trạng thái đơn hàng (Dành cho Quản trị viên)")
public class UpdateOrderStatusRequest {

    @NotNull(message = "Trạng thái không được để trống")
    @Schema(description = "Trạng thái đơn hàng mới (PENDING, CONFIRMED, PROCESSING, SHIPPED, DELIVERED, CANCELLED, REFUNDED)", example = "CONFIRMED", requiredMode = Schema.RequiredMode.REQUIRED)
    private OrderStatus status;

    @Size(max = 500, message = "Ghi chú không được vượt quá 500 ký tự")
    @Schema(description = "Ghi chú hoặc lý do thay đổi trạng thái", example = "Đã xác nhận đơn hàng qua điện thoại.")
    private String notes;
}
