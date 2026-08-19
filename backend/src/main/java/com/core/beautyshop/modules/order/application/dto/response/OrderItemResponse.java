package com.core.beautyshop.modules.order.application.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class OrderItemResponse {
    private Long id;
    private Long variantId;
    private String variantName;
    private Integer quantity;
    private BigDecimal price;
    private BigDecimal discount;

}
