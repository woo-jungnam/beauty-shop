package com.core.beautyshop.modules.order.api.dto;

import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;

/** Prices come from the performed appointment item, never from the checkout request. */
@Schema(description = "Một mục dịch vụ PERFORMED ngoài vé, số lượng luôn 1; snapshot được đọc từ appointment, client không nhập giá")
public record SpaVisitCharge(Long appointmentItemId, Long serviceId,
                            @Schema(description = "Tên snapshot lúc book, không phụ thuộc đổi tên catalog") String serviceName,
                            @Schema(description = "Giá snapshot gốc VND, chưa làm tròn riêng từng mục") BigDecimal unitPrice) { }
