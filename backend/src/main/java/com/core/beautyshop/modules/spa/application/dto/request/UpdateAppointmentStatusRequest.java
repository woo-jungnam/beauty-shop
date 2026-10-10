package com.core.beautyshop.modules.spa.application.dto.request;

import com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Chuyển trạng thái buổi; không thay thế việc check-in và ghi execution từng item")
public class UpdateAppointmentStatusRequest {
    @jakarta.validation.constraints.Size(max = 50)
    @Schema(description = "Map appointmentItemId→Staff.id (không phải User.id), tối đa 50 mục. Chỉ khi status đích CONFIRMED, kể cả cùng CONFIRMED; therapist không được gửi", example = "{\"101\":1,\"102\":2}")
    private java.util.Map<Long, Long> staffAssignments;

    @NotNull(message = "Trạng thái lịch hẹn không được để trống")
    @Schema(description = "Trạng thái đích tuân transition. IN_PROGRESS cần check-in/đến giờ; COMPLETED cần mọi item PERFORMED/SKIPPED; NO_SHOW chưa check-in và quá grace", example = "CONFIRMED")
    private AppointmentStatus status;

    @Schema(description = "Lý do bắt buộc khi NO_SHOW/CANCEL qua action này; ghi chú lịch sau nối tối đa 500 ký tự. SPA_THERAPIST phải bỏ trường này", example = "Đã liên hệ khách để xác nhận lịch")
    private String notes;
}
