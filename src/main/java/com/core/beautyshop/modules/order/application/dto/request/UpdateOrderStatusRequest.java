package com.core.beautyshop.modules.order.application.dto.request;

import com.core.beautyshop.modules.order.domain.enums.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Điều phối đơn ADMIN/ORDER_STAFF; không dùng cho invoice Spa COMPLETED. Trả tiền dùng refund-confirmation, không có OrderStatus REFUNDED")
public class UpdateOrderStatusRequest {

    @NotNull(message = "Trạng thái không được để trống")
    @Schema(description = "Phải tuân thủ chuyển trạng thái theo route; PENDING chỉ hợp lệ khi đơn đã PENDING (gửi lại), COMPLETED không được đặt qua API này", allowableValues = {"PENDING", "CONFIRMED", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED", "RETURNED"}, example = "CONFIRMED", requiredMode = Schema.RequiredMode.REQUIRED)
    private OrderStatus status;

    @Size(max = 500, message = "Ghi chú không được vượt quá 500 ký tự")
    @Schema(description = "Ghi chú; bắt buộc không trống khi CANCELLED", example = "Khách yêu cầu hủy đơn", maxLength = 500)
    private String notes;

    @Size(max = 100)
    @Schema(description = "Tên hãng vận chuyển khi SHIPPED; gửi cùng trackingCode hoặc cùng để trống", maxLength = 100)
    private String carrierName;

    @Size(max = 100)
    @Schema(description = "Mã vận đơn khi SHIPPED; gửi cùng carrierName hoặc cùng để trống", maxLength = 100)
    private String trackingCode;
}
