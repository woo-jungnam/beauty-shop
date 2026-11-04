package com.core.beautyshop.modules.order.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.core.beautyshop.shared.domain.Base;
import com.core.beautyshop.modules.order.domain.enums.OrderStatus;
import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import com.core.beautyshop.modules.order.domain.enums.PaymentStatus;
import com.core.beautyshop.shared.exception.BusinessException;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order extends Base {

    @Column(name = "order_number", nullable = false, unique = true, length = 50)
    private String orderNumber;

    @Column(name = "checkout_key", length = 64, unique = true)
    private String checkoutKey;

    @Column(name = "checkout_hash", length = 64)
    private String checkoutHash;

    @Column(name = "payment_deadline")
    private Instant paymentDeadline;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "refunded_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(name = "refund_reference", length = 100)
    private String refundReference;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "guest_session_id", length = 255)
    private String guestSessionId;

    @Column(name = "service_package_id")
    private Long servicePackageId;
    @Column(name = "spa_benefit_type", length = 30)
    private String spaBenefitType;

    @Column(name = "appointment_id", unique = true)
    private Long appointmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "customer_name", nullable = false, length = 100)
    private String customerName;

    @Column(name = "customer_phone", nullable = false, length = 20)
    private String customerPhone;

    @Column(name = "customer_email", length = 150)
    private String customerEmail;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "shipping_address", nullable = false, length = 500)
    private String shippingAddress;

    @Column(name = "ward", length = 100)
    private String ward;

    @Column(name = "district", length = 100)
    private String district;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "sub_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal subTotal;

    @Column(name = "shipping_fee", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "paid_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "carrier_name", length = 100)
    private String carrierName;

    @Column(name = "tracking_code", length = 100)
    private String trackingCode;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "voucher_id")
    private Long voucherId;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<OrderItem> items;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SpaVisitInvoiceItem> spaVisitItems;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderStatusHistory> statusHistories;

    public boolean isSpaOrder() {
        return this.servicePackageId != null || this.appointmentId != null || (this.spaVisitItems != null && !this.spaVisitItems.isEmpty());
    }

    public void cancel(String reason, Long cancelledByUserId) {
        if (this.status == OrderStatus.CANCELLED) {
            return;
        }
        if (this.status == OrderStatus.DELIVERED || this.status == OrderStatus.COMPLETED) {
            throw new BusinessException("Không thể hủy đơn hàng đã hoàn tất hoặc đã giao thành công");
        }
        this.cancelReason = reason;
        this.cancelledBy = cancelledByUserId;
        this.status = OrderStatus.CANCELLED;
        if (this.paidAmount != null && this.refundedAmount != null && this.paidAmount.compareTo(this.refundedAmount) > 0) {
            this.paymentStatus = PaymentStatus.REFUND_PENDING;
        }
    }

    public void markAsShipped(String carrier, String tracking) {
        if (this.status != OrderStatus.CONFIRMED && this.status != OrderStatus.PROCESSING) {
            throw new BusinessException("Chỉ có thể giao đơn hàng đang ở trạng thái đã xác nhận hoặc đang xử lý");
        }
        if (carrier != null && !carrier.isBlank()) {
            this.carrierName = carrier.trim();
        }
        if (tracking != null && !tracking.isBlank()) {
            this.trackingCode = tracking.trim();
        }
        this.status = OrderStatus.SHIPPED;
    }

    public void markAsDelivered(Instant deliveredTime) {
        if (this.status != OrderStatus.SHIPPED) {
            throw new BusinessException("Chỉ có thể xác nhận đã giao khi đơn hàng đang được vận chuyển");
        }
        this.status = OrderStatus.DELIVERED;
        if (this.deliveredAt == null) {
            this.deliveredAt = deliveredTime != null ? deliveredTime : Instant.now();
        }
    }
}
