package com.core.beautyshop.modules.cart.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Dữ liệu yêu cầu thêm sản phẩm vào giỏ hàng")
public class AddToCartRequest {

    @NotNull(message = "ID biến thể không được để trống")
    @Schema(description = "ID của biến thể SKU cần thêm", example = "201", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long variantId;

    @NotNull(message = "Số lượng không được để trống")
    @Min(value = 1, message = "Số lượng phải lớn hơn hoặc bằng 1")
    @Schema(description = "Số lượng cần thêm (tối thiểu là 1)", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer quantity;

    @Schema(description = "ID phiên giỏ hàng vãng lai (nếu chưa đăng nhập)", example = "guest-session-uuid-12345")
    private String sessionId;
}
