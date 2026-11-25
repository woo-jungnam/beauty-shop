package com.core.beautyshop.modules.order.application.dto.response;

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
@Schema(description = "Thông tin phiếu thu hồi hàng hoàn hiển thị cho tài xế Shipper")
public class ShipperReturnView {
    private Long id;
    private Long orderId;
    private String orderNumber;
    private String status;
    private String returnMethod;
    private String reason;
    private String decisionReason;
    private BigDecimal refundAmount;
    private String customerName;
    private String customerPhone;
    private String pickupAddress;
    private PaymentMethod paymentMethod;
    private Long pickupShipperId;
    private String pickupShipperName;
    private String pickupShipperPhone;
    private Instant pickupAssignedAt;
    private Instant pickupStartedAt;
    private Instant pickedUpAt;
    private String pickupProofUrl;
    private String pickupNotes;
    private String pickupFailureReason;
    private int pickupAttemptCount;
    private Instant handoverRequestedAt;
    private String handoverNotes;
    private Instant receivedAt;
    private Instant createdAt;
    private List<String> evidenceUrls;
    private List<String> itemsSummary;
    private List<ShipperReturnItemView> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShipperReturnItemView {
        private Long orderItemId;
        private Long productId;
        private Long variantId;
        private String sku;
        private String productName;
        private String variantName;
        private String imageUrl;
        private int returnQuantity;
        private int purchasedQuantity;
        private BigDecimal unitPrice;
    }
}

