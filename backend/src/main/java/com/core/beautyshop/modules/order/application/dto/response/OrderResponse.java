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
    @Schema(description = "Chủ đơn tài khoản; null với đơn khách vãng lai")
    private Long userId;

    @Schema(description = "ID gói liệu trình Spa nếu đây là đơn mua gói")
    private Long servicePackageId;
    @Schema(description = "ID appointment nếu là invoice buổi lẻ; status giữ COMPLETED độc lập với trạng thái thanh toán")
    private Long appointmentId;
    @Schema(description = "Snapshot mục PERFORMED không dùng vé của invoice Spa; tách khỏi items sản phẩm")
    private List<com.core.beautyshop.modules.order.api.dto.SpaVisitCharge> spaVisitItems;

    @Schema(description = "Mã số đơn dùng trong nội dung chuyển khoản", example = "ORD-1234ABCD")
    private String orderNumber;

    @Schema(description = "Trạng thái phục vụ/giao hàng; COMPLETED dành cho invoice Spa, RETURNED là hàng đã trả. Khác paymentStatus", example = "PENDING")
    private OrderStatus status;

    @Schema(description = "Phương thức thanh toán đã chọn", example = "BANK")
    private PaymentMethod paymentMethod;

    @Schema(description = "PENDING gồm chưa đủ tiền; PAID đủ nghĩa vụ; REFUND_PENDING đang chờ xử lý tiền cần hoàn; REFUNDED đã ghi nhận hoàn", example = "PENDING")
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

    @Schema(description = "Nghĩa vụ gốc, tổng cuối làm tròn nguyên VND HALF_UP; không phải số còn thiếu", example = "830000")
    private BigDecimal totalAmount;
    @Schema(description = "Tiền thực thu tích lũy, có thể một phần hoặc lớn hơn nghĩa vụ; không bị giảm khi hoàn")
    private BigDecimal paidAmount;
    @Schema(description = "Tổng tiền ADMIN đã xác nhận hoàn thủ công")
    private BigDecimal refundedAmount;
    @Schema(description = "Mã chứng từ xác nhận hoàn gần nhất")
    private String refundReference;
    @Schema(description = "Hạn trả BANK của đơn chờ thanh toán; null nếu đã PAID hoặc invoice buổi Spa")
    private Instant paymentDeadline;
    @Schema(description = "UTC Instant lần đầu nghĩa vụ trở thành PAID; không đổi khi retry/hoàn tiền, legacy có thể null")
    private Instant paidAt;
    @Schema(description = "ID voucher đã áp lúc checkout")
    private Long voucherId;
    @Schema(description = "Hãng vận chuyển nếu được ghi khi SHIPPED")
    private String carrierName;
    @Schema(description = "Mã vận đơn")
    private String trackingCode;
    @Schema(description = "Lý do hủy được ghi nhận")
    private String cancelReason;
    @Schema(description = "ID actor hủy; có thể null với tác vụ hệ thống")
    private Long cancelledBy;
    @Schema(description = "Ghi chú lúc tạo đơn/invoice")
    private String notes;
    @Schema(description = "Lịch sử trạng thái và ghi chú xử lý")
    private List<OrderStatusHistoryResponse> statusHistories;

    @Schema(description = "Thời gian tạo đơn (UTC Instant)", example = "2026-09-06T09:30:00Z")
    private Instant createdAt;

    @Schema(description = "Snapshot mặt hàng sản phẩm; invoice Spa dùng spaVisitItems và không có dòng sản phẩm")
    private List<OrderItemResponse> items;

    @Schema(description = "Hướng dẫn khi còn cần thanh toán; có thể null khi đã PAID/hoàn hoặc ở phản hồi danh sách. Invoice BANK dùng số còn thiếu cho QR")
    private PaymentInstruction paymentInstruction;
}
