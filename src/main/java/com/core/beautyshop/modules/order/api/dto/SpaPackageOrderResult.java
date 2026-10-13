package com.core.beautyshop.modules.order.api.dto;

import com.core.beautyshop.modules.payment.api.dto.PaymentInstruction;
import com.core.beautyshop.modules.order.domain.enums.OrderStatus;
import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import com.core.beautyshop.modules.order.domain.enums.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;

@Value
@Builder
@Schema(description = "Đơn mua gói Spa, khác invoice buổi lẻ; không giao hàng vật lý, chỉ cấp vé sau PAID")
public class SpaPackageOrderResult {
    Long orderId;
    String orderNumber;
    Long servicePackageId;
    @Schema(description = "Nghĩa vụ nguyên VND, tổng cuối làm tròn HALF_UP")
    BigDecimal totalAmount;
    OrderStatus status;
    PaymentMethod paymentMethod;
    PaymentStatus paymentStatus;
    @Schema(description = "UTC Instant deadline đơn chưa thanh toán; null nếu PAID")
    Instant paymentDeadline;
    Instant createdAt;
    PaymentInstruction paymentInstruction;
}
