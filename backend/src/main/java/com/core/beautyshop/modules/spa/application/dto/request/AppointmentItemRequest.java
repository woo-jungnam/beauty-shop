package com.core.beautyshop.modules.spa.application.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Schema(description = "Một dịch vụ trong booking, thực hiện tuần tự theo thứ tự items")
public class AppointmentItemRequest {

    @NotNull(message = "Mã dịch vụ không được để trống")
    @Schema(description = "Dịch vụ hoạt động/chưa xóa", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long serviceId;

    @Schema(description = "ID hồ sơ Staff, không phải User.id; null để lễ tân phân công khi confirm", example = "1")
    private Long staffId;

    @Schema(description = "Vé paid thuộc tài khoản đặt, có lượt khả dụng cho serviceId; null là buổi lẻ. Book giữ quota, chưa redeem", example = "101")
    private Long ticketId;
    @Schema(description = "Tài nguyên cụ thể yêu cầu ưu tiên bắt buộc khi book; phải phù hợp type và còn capacity. Các loại khác vẫn tự phân bổ nếu cần", example = "1")
    private Long facilityId;
}
