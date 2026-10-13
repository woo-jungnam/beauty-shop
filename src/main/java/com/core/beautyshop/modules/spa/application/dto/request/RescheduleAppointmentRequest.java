package com.core.beautyshop.modules.spa.application.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Schema(description = "Đổi ngày/giờ PENDING hoặc CONFIRMED chưa check-in; giữ danh sách dịch vụ, giá và duration đã đặt")
public class RescheduleAppointmentRequest {
    @NotNull(message = "Ngày hẹn không được để trống")
    @jakarta.validation.constraints.FutureOrPresent(message = "Ngày hẹn phải từ hôm nay trở đi")
    @Schema(description = "Ngày mới tại Việt Nam; kết hợp startTime phải ở tương lai", type = "string", format = "date", example = "2026-10-06")
    private LocalDate appointmentDate;

    @NotNull(message = "Giờ hẹn không được để trống")
    @Schema(description = "Giờ mới tại Asia/Ho_Chi_Minh, tổng interval phải trong 08:00–20:00", type = "string", format = "time", example = "11:00:00")
    private LocalTime startTime;

    @Size(max = 500)
    @Schema(description = "Lý do bắt buộc khi nhân viên đổi cho khách khác hoặc vượt cutoff", maxLength = 500, example = "Đổi giờ theo yêu cầu khách")
    private String notes;
}
