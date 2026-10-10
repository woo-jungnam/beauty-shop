package com.core.beautyshop.modules.spa.application.dto.request;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Schema(description = "Đặt yêu cầu PENDING cho chính tài khoản hiện tại; giờ Việt Nam, không có customerId hoặc idempotency key")
public class BookAppointmentRequest {
    
    @NotNull(message = "Ngày hẹn không được để trống")
    @FutureOrPresent(message = "Ngày hẹn phải từ hôm nay trở đi")
    @Schema(description = "Ngày hẹn tại Asia/Ho_Chi_Minh; kết hợp startTime phải ở tương lai", type = "string", format = "date", example = "2026-10-05")
    private LocalDate appointmentDate;
    
    @NotNull(message = "Giờ bắt đầu không được để trống")
    @Schema(description = "Giờ bắt đầu tại Việt Nam; toàn bộ items gồm chuẩn bị phải nằm trong 08:00–20:00", type = "string", format = "time", example = "10:00:00")
    private LocalTime startTime;
    
    @Size(max = 500)
    @Schema(description = "Ghi chú đặt lịch", maxLength = 500, example = "Khách muốn trao đổi quy trình trước buổi")
    private String notes;

    @Schema(description = "ID khách hàng nếu Lễ tân hoặc Quản trị viên đặt lịch hộ; người dùng thông thường luôn đặt cho chính mình", example = "10")
    private Long targetUserId;
    
    @NotEmpty(message = "Cần chọn ít nhất một dịch vụ để đặt lịch")
    @Schema(description = "Ít nhất một item, thứ tự là thứ tự dịch vụ dự kiến; thời lượng và giá lấy từ catalog lúc đặt")
    private List<@Valid AppointmentItemRequest> items;
}
