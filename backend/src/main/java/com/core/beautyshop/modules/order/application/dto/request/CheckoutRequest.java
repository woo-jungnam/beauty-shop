package com.core.beautyshop.modules.order.application.dto.request;

import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "Dữ liệu yêu cầu thanh toán và khởi tạo đơn hàng mới")
public class CheckoutRequest {
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Schema(hidden = true)
    private String idempotencyKey;

    @Schema(description = "ID phiên giỏ hàng (áp dụng cho khách vãng lai chưa đăng nhập, nếu đã đăng nhập thì để null)", example = "guest-session-uuid-12345")
    private String sessionId;

    @NotBlank(message = "Tên khách hàng không được để trống")
    @Schema(description = "Họ tên người nhận hàng", example = "Trần Thị Mai", requiredMode = Schema.RequiredMode.REQUIRED)
    private String customerName;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = "^(0|\\+84)[35789][0-9]{8}$", message = "Định dạng số điện thoại không hợp lệ")
    @Schema(description = "Số điện thoại người nhận (định dạng Việt Nam)", example = "0987654321", requiredMode = Schema.RequiredMode.REQUIRED)
    private String customerPhone;

    @NotBlank(message = "Địa chỉ giao hàng không được để trống")
    @Schema(description = "Địa chỉ chi tiết (số nhà, tên đường)", example = "123 Đường Nguyễn Huệ", requiredMode = Schema.RequiredMode.REQUIRED)
    private String shippingAddress;

    @Schema(description = "Phường / Xã", example = "Phường Bến Nghé")
    private String ward;

    @Schema(description = "Quận / Huyện", example = "Quận 1")
    private String district;

    @Schema(description = "Tỉnh / Thành phố", example = "TP. Hồ Chí Minh")
    private String city;

    @NotNull(message = "Phương thức thanh toán không được để trống")
    @Schema(description = "Phương thức thanh toán: COD (tiền mặt khi nhận), BANK (chuyển khoản VietQR/SePay)", example = "BANK", requiredMode = Schema.RequiredMode.REQUIRED)
    private PaymentMethod paymentMethod;

    @Schema(description = "Ghi chú thêm cho đơn hàng khi giao hàng", example = "Giao hàng trong giờ hành chính.")
    private String notes;
}
