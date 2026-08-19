package com.core.beautyshop.modules.order.application.dto.request;

import com.core.beautyshop.modules.order.domain.enums.OrderStatus;
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
public class UpdateOrderStatusRequest {

    @NotNull(message = "Trạng thái không được để trống")
    private OrderStatus status;

    @Size(max = 500)
    private String notes;
}
