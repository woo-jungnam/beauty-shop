package com.core.beautyshop.modules.order.application.dto.request;

import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Checkout giỏ sản phẩm; BANK/COD. Idempotency-Key là header, không phải body. Không dùng DTO này để tạo invoice Spa")
public class CheckoutRequest {
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Schema(hidden = true)
    private String idempotencyKey;

    @Schema(description = "Phiên giỏ khách vãng lai; body được ưu tiên, header X-Guest-Session-Id chỉ dùng khi trường này trống. Người đăng nhập dùng giỏ tài khoản", example = "guest-session-uuid-12345")
    private String sessionId;

    @NotBlank(message = "Tên khách hàng không được để trống")
    @Schema(description = "Họ tên người nhận hàng", example = "Trần Thị Mai", requiredMode = Schema.RequiredMode.REQUIRED)
    @Size(max = 100)
    private String customerName;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = "^(0|\\+84)[35789][0-9]{8}$", message = "Định dạng số điện thoại không hợp lệ")
    @Schema(description = "Số điện thoại người nhận (định dạng Việt Nam)", example = "0987654321", requiredMode = Schema.RequiredMode.REQUIRED)
    private String customerPhone;

    @NotBlank(message = "Địa chỉ giao hàng không được để trống")
    @Schema(description = "Địa chỉ chi tiết (số nhà, tên đường)", example = "123 Đường Nguyễn Huệ", requiredMode = Schema.RequiredMode.REQUIRED)
    @Size(max = 500)
    private String shippingAddress;

    @Schema(description = "Phường / Xã", example = "Phường Bến Nghé", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Phường / xã không được để trống")
    @Size(max = 100)
    private String ward;

    @Schema(description = "Quận / Huyện", example = "Quận 1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Quận / huyện không được để trống")
    @Size(max = 100)
    private String district;

    @Schema(description = "Tỉnh / Thành phố", example = "TP. Hồ Chí Minh", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Tỉnh / thành phố không được để trống")
    @Size(max = 100)
    private String city;

    @NotNull(message = "Phương thức thanh toán không được để trống")
    @Schema(description = "COD thu khi giao; BANK chuyển khoản SePay. CASH bị từ chối ở checkout sản phẩm", allowableValues = {"COD", "BANK"}, example = "BANK", requiredMode = Schema.RequiredMode.REQUIRED)
    private PaymentMethod paymentMethod;

    @Schema(description = "Ghi chú thêm cho đơn hàng khi giao hàng", example = "Giao hàng trong giờ hành chính.")
    @Size(max = 500)
    private String notes;

    @Size(max = 50)
    @Schema(description = "Mã voucher tùy chọn; chỉ tài khoản đăng nhập dùng được. Kiểm thời gian, hạn ngạch và số tiền tối thiểu", maxLength = 50, example = "WELCOME10")
    private String voucherCode;
}
