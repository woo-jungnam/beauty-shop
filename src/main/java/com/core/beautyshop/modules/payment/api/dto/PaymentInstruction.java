package com.core.beautyshop.modules.payment.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Hướng dẫn thanh toán; không phải chứng từ đã nhận tiền. Các trường ngân hàng/QR thường chỉ có cho BANK")
public class PaymentInstruction {
    @Schema(description = "BANK/COD/CASH theo tuyến dùng", example = "BANK")
    private String method;
    @Schema(description = "Thông báo hướng dẫn cho khách")
    private String instructionMessage;
    @Schema(description = "Ngân hàng nhận chuyển khoản")
    private String bankName;
    @Schema(description = "Tên chủ tài khoản nhận")
    private String bankAccountName;
    @Schema(description = "Số tài khoản nhận")
    private String bankAccountNumber;
    @Schema(description = "Nội dung chuyển khoản cần giữ nguyên để webhook khớp order", example = "ORD-1234ABCD")
    private String transferSyntax;
    @Schema(description = "URL VietQR; invoice Spa có số tiền còn thiếu tại thời điểm đọc")
    private String qrCodeUrl;
}
