package com.core.beautyshop.modules.catalog.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Yêu cầu ghi nhận hành vi tương tác của người dùng")
public class InteractionTrackingRequest {

    @NotNull(message = "ID sản phẩm không được để trống")
    @Schema(description = "ID sản phẩm tương tác", example = "1")
    private Long productId;

    @NotBlank(message = "Loại hành vi không được để trống")
    @Schema(description = "Loại hành vi do client gửi: VIEW hoặc SEARCH_CLICK. Thêm giỏ và mua hàng do backend tự ghi nhận.", allowableValues = {"VIEW", "SEARCH_CLICK"}, example = "VIEW")
    private String actionType;

    @Schema(description = "Mã phiên làm việc ẩn danh của khách (nếu chưa đăng nhập)", example = "guest_abc123")
    private String sessionId;
}
