package com.core.beautyshop.modules.order.application.dto.response;

import com.core.beautyshop.modules.order.domain.enums.OrderStatus;
import com.core.beautyshop.modules.order.domain.enums.PaymentStatus;
import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin đơn hàng hiển thị trên ứng dụng bưu tá Shipper")
public class ShipperOrderView {
    private Long id;
    private String orderNumber;
    private String customerName;
    private String customerPhone;
    private String shippingAddress;
    private BigDecimal totalAmount;
    private BigDecimal subTotal;
    private BigDecimal shippingFee;
    private BigDecimal discountAmount;
    private BigDecimal paidAmount;
    private BigDecimal codAmount;
    private BigDecimal codCollected;
    private BigDecimal codRemitted;
    private Instant deliveryStartedAt;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    private OrderStatus status;
    private String deliveryProofUrl;
    private String deliveredNotes;
    private String notes;
    private String carrierName;
    private String trackingCode;
    private String cancelReason;
    private Instant paidAt;
    private Instant createdAt;
    private Instant deliveredAt;
    private Instant assignedAt;
    private List<String> itemsSummary;
    private List<OrderItemResponse> items;
}

