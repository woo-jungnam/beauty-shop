package com.core.beautyshop.modules.order.application.dto.response;

import com.core.beautyshop.modules.order.domain.enums.OrderStatus;
import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Bản ghi trạng thái/ghi chú order; createdAt là UTC Instant, ghi nhận thu/hoàn có thể giữ nguyên status phục vụ")
public record OrderStatusHistoryResponse(OrderStatus status, String notes, Instant createdAt) { }
