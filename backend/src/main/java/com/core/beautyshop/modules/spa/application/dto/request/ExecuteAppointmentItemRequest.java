package com.core.beautyshop.modules.spa.application.dto.request;

import com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus;
import jakarta.validation.constraints.*;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Schema(description = "Ghi execution của item trong buổi IN_PROGRESS; không dùng LEGACY_FINALIZED/NO_SHOW làm request target")
public class ExecuteAppointmentItemRequest {
    @Schema(description = "PLANNED→IN_PROGRESS→PERFORMED hoặc PLANNED→SKIPPED. Gửi lại cùng trạng thái không trừ quota thêm", allowableValues = {"IN_PROGRESS", "PERFORMED", "SKIPPED"}, example = "IN_PROGRESS")
    @NotNull private AppointmentItemExecutionStatus status;
    @Schema(description = "Reason bắt buộc khi SKIPPED; không biểu diễn dừng giữa chừng bằng PERFORMED", maxLength = 2000, example = "Khách chọn bỏ dịch vụ này trước khi thực hiện")
    @Size(max = 2000) private String notes;
}
