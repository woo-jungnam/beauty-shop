package com.core.beautyshop.modules.spa.api;
import com.core.beautyshop.modules.spa.application.service.SpaBusinessCalendarService;
import com.core.beautyshop.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController @RequiredArgsConstructor
@Tag(name = "Lịch hoạt động Spa (Business Calendar)", description = "Tra cứu khung giờ mở cửa, lịch làm việc hàng tuần và ngày nghỉ ngoại lệ của Spa")
public class SpaCalendarController {
    private final SpaBusinessCalendarService calendar;

    @Operation(summary = "Xem lịch hoạt động Spa theo ngày", description = "Kiểm tra giờ mở cửa, đóng cửa và các khung giờ phục vụ trong ngày đã chọn")
    @GetMapping("/api/v1/spa/business-calendar")
    public ApiResponse<SpaBusinessCalendarService.Window> date(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date) { return ApiResponse.success(calendar.forDate(date)); }

    @Operation(summary = "Xem lịch làm việc hàng tuần (Admin/Staff)", description = "Lấy cấu hình khung giờ làm việc chuẩn cho từng thứ trong tuần của Spa")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')") @GetMapping("/api/v1/admin/spa/calendar/weekly")
    public ApiResponse<List<SpaBusinessCalendarService.Hours>> weekly() { return ApiResponse.success(calendar.weekly()); }

    @Operation(summary = "Cập nhật lịch làm việc hàng tuần (Admin)", description = "Cấu hình giờ mở cửa, đóng cửa và thời gian hoạt động định kỳ theo thứ")
    @PreAuthorize("hasRole('ADMIN')") @PutMapping("/api/v1/admin/spa/calendar/weekly")
    public ApiResponse<List<SpaBusinessCalendarService.Hours>> weekly(@RequestBody List<SpaBusinessCalendarService.Hours> rows) { return ApiResponse.success(calendar.saveWeekly(rows)); }

    @Operation(summary = "Danh sách ngày nghỉ và giờ làm việc ngoại lệ (Admin/Staff)", description = "Tra cứu các ngày lễ tết, sự kiện hoặc khung giờ đặc biệt trong khoảng thời gian")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')") @GetMapping("/api/v1/admin/spa/calendar/exceptions")
    public ApiResponse<List<SpaBusinessCalendarService.Window>> exceptions(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to) { return ApiResponse.success(calendar.exceptions(from,to)); }

    @Operation(summary = "Thiết lập ngày hoạt động ngoại lệ (Admin)", description = "Đặt lịch đóng cửa nghỉ lễ hoặc đổi giờ mở cửa cho một ngày cụ thể")
    @PreAuthorize("hasRole('ADMIN')") @PutMapping("/api/v1/admin/spa/calendar/exceptions/{date}")
    public ApiResponse<SpaBusinessCalendarService.Window> save(@PathVariable @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,@RequestBody SpaBusinessCalendarService.ExceptionCommand c) { return ApiResponse.success(calendar.saveException(date,c)); }

    @Operation(summary = "Xóa ngày hoạt động ngoại lệ (Admin)", description = "Hủy bỏ cấu hình ngoại lệ và quay lại lịch làm việc hàng tuần chuẩn")
    @PreAuthorize("hasRole('ADMIN')") @DeleteMapping("/api/v1/admin/spa/calendar/exceptions/{date}")
    public ApiResponse<Void> delete(@PathVariable @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date) { calendar.deleteException(date); return ApiResponse.success(null); }
}
