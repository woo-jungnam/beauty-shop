package com.core.beautyshop.modules.order.api.dto;

import com.core.beautyshop.modules.order.domain.enums.*;
import com.core.beautyshop.modules.payment.api.dto.PaymentInstruction;
import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Invoice cố định của appointment COMPLETED; chỉ snapshot PERFORMED ngoài vé. Thanh toán độc lập với kết quả phục vụ")
public record SpaVisitInvoiceResult(
        @Schema(description = "ID order dùng khi xác nhận đã hoàn thủ công") Long orderId, String orderNumber, Long appointmentId, Long userId,
        @Schema(description = "Giữ COMPLETED kể cả khi còn thiếu tiền hoặc đã hoàn", allowableValues = "COMPLETED") OrderStatus status,
        @Schema(description = "Phương thức chọn lúc tạo, BANK/CASH", allowableValues = {"BANK", "CASH"}) PaymentMethod paymentMethod, PaymentStatus paymentStatus,
        @Schema(description = "Tổng giá snapshot gốc trước làm tròn, VND") BigDecimal subTotal,
        @Schema(description = "Nghĩa vụ gốc: subTotal làm tròn nguyên VND HALF_UP một lần") BigDecimal totalAmount,
        @Schema(description = "Tiền thực thu tích lũy BANK+CASH, không bị giảm sau hoàn") BigDecimal paidAmount,
        @Schema(description = "Tổng đã xác nhận hoàn thủ công") BigDecimal refundedAmount,
        @Schema(description = "max(totalAmount−paidAmount,0); tiền hoàn không tự tạo công nợ mới") BigDecimal amountDue,
        @Schema(description = "UTC Instant nghĩa vụ lần đầu PAID; giữ nguyên khi retry/hoàn") Instant paidAt, Instant createdAt,
        String notes, List<SpaVisitCharge> items,
        @Schema(description = "Null khi PAID/hoàn; BANK QR số còn thiếu, CASH hướng dẫn thu tại quầy") PaymentInstruction paymentInstruction) { }
