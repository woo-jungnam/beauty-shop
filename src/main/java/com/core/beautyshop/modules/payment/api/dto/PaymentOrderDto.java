package com.core.beautyshop.modules.payment.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOrderDto {
    private String orderNumber;
    private BigDecimal totalAmount;
    private String customerName;
}
