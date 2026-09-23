package com.core.beautyshop.modules.order.application.dto.response;

import com.core.beautyshop.modules.order.domain.enums.OrderStatus;
import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import com.core.beautyshop.modules.order.domain.enums.PaymentStatus;
import com.core.beautyshop.modules.payment.api.dto.PaymentInstruction;
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
@Schema(description = "Thông tin chi tiết của đơn hàng sau khi tạo hoặc truy vấn")
public class OrderResponse {

    @Schema(description = "ID đơn hàng", example = "1001")
    private Long id;

    @Schema(description = "Mã số đơn hàng hiển thị (Order Number)", example = "ORD-20260906-8921")
    private String orderNumber;

    @Schema(description = "Trạng thái đơn hàng hiện tại (PENDING, CONFIRMED, PROCESSING, SHIPPED, DELIVERED, CANCELLED)", example = "PENDING")
    private OrderStatus status;

    @Schema(description = "Phương thức thanh toán đã chọn", example = "BANK")
    private PaymentMethod paymentMethod;

    @Schema(description = "Trạng thái thanh toán (PENDING, PAID, FAILED, REFUNDED)", example = "PENDING")
    private PaymentStatus paymentStatus;

    @Schema(description = "Họ tên người nhận hàng", example = "Trần Thị Mai")
    private String customerName;

    @Schema(description = "Số điện thoại nhận hàng", example = "0987654321")
    private String customerPhone;

    @Schema(description = "Địa chỉ giao hàng đầy đủ", example = "123 Đường Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh")
    private String shippingAddress;

    @Schema(description = "Tổng tiền hàng trước giảm giá và ship (VND)", example = "850000")
    private BigDecimal subTotal;

    @Schema(description = "Phí vận chuyển (VND)", example = "30000")
    private BigDecimal shippingFee;

    @Schema(description = "Số tiền giảm giá / Voucher (VND)", example = "50000")
    private BigDecimal discountAmount;

    @Schema(description = "Tổng số tiền thực tế khách cần thanh toán (VND)", example = "830000")
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal refundedAmount;
    private String refundReference;
    private Instant paymentDeadline;

    @Schema(description = "Thời gian tạo đơn (UTC Instant)", example = "2026-09-06T09:30:00Z")
    private Instant createdAt;

    @Schema(description = "Danh sách chi tiết các mặt hàng đã đặt")
    private List<OrderItemResponse> items;

    @Schema(description = "Hướng dẫn thanh toán (Chứa mã QR VietQR, số tài khoản, nội dung chuyển khoản nếu thanh toán online)")
    private PaymentInstruction paymentInstruction;
}
