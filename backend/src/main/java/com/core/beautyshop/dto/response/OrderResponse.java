package com.core.beautyshop.dto.response;

import com.core.beautyshop.entities.order.enums.OrderStatus;
import com.core.beautyshop.entities.order.enums.PaymentMethod;
import com.core.beautyshop.entities.order.enums.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class OrderResponse {
    private Long id;
    private String orderNumber;
    private OrderStatus status;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    
    private String customerName;
    private String customerPhone;
    private String shippingAddress;
    
    private BigDecimal subTotal;
    private BigDecimal shippingFee;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    
    private Instant createdAt;
    
    private List<OrderItemResponse> items;
    
    private PaymentInstruction paymentInstruction;

}
