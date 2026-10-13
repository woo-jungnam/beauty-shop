package com.core.beautyshop.modules.spa.api;

import com.core.beautyshop.modules.spa.application.service.AdminStaffService;
import com.core.beautyshop.shared.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springdoc.core.annotations.ParameterObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Chuyên viên & Lịch trực Spa (Admin)", description = "Chỉ ADMIN hoặc STAFF; ID staff là hồ sơ nhân viên, userId là tài khoản liên kết")
@RestController
@RequestMapping("/api/v1/admin/staff")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
@RequiredArgsConstructor
public class AdminStaffController {
    private final AdminStaffService service;

    @Operation(summary = "Lấy danh sách chuyên viên Spa", description = "Chỉ ADMIN hoặc STAFF; hồ sơ staff chưa xóa gồm inactive, kèm tài khoản/kỹ năng. Danh sách không phân trang.")
    @GetMapping
    public ApiResponse<List<AdminStaffService.StaffView>> list() {
        return ApiResponse.success(service.list());
    }

    @Operation(summary = "Xem chi tiết chuyên viên Spa theo ID", description = "Chỉ ADMIN/STAFF/RECEPTION; id là Staff.id, không phải User.id; không xem hồ sơ đã xóa.")
    @GetMapping("/{id}")
    public ApiResponse<AdminStaffService.StaffView> getById(@PathVariable Long id) {
        return ApiResponse.success(service.get(id));
    }

    @Operation(summary = "Thêm hồ sơ chuyên viên Spa", description = "ADMIN/STAFF; userId là tài khoản đã tồn tại và chưa gắn hồ sơ staff khác. Tạo hồ sơ không tự cấp ROLE_STAFF hoặc kỹ năng/ca; active thiếu/null là true.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<AdminStaffService.StaffView> create(@RequestBody AdminStaffService.StaffCommand command) {
        return ApiResponse.created(service.save(null, command), "Staff created");
    }

    @Operation(summary = "Cập nhật hồ sơ chuyên viên Spa", description = "ADMIN/STAFF; userId phải tồn tại và duy nhất. Chặn đổi tài khoản/ngưng hoạt động khi còn lịch được giao từ hôm nay về sau; cần phân công lại/hủy trước. active thiếu/null là true, không mang nghĩa giữ giá trị cũ.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<AdminStaffService.StaffView> update(@PathVariable Long id, @RequestBody AdminStaffService.StaffCommand command) {
        return ApiResponse.success(service.save(id, command));
    }

    @Operation(summary = "Xóa mềm chuyên viên Spa", description = "ADMIN/STAFF; chặn khi còn lịch được giao từ hôm nay về sau. Không xóa tài khoản User hoặc tự hủy lịch khách.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }

    @Operation(summary = "Gán hoặc cập nhật kỹ năng dịch vụ", description = "ADMIN/STAFF; serviceId là dịch vụ chưa xóa. certified là thông tin kỹ năng, không tự cấp role hoặc ca làm việc; kỹ năng hiện có được cập nhật.")
    @PutMapping("/{id}/skills")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<AdminStaffService.StaffView> skill(@PathVariable Long id, @RequestBody AdminStaffService.SkillCommand command) {
        return ApiResponse.success(service.assignSkill(id, command));
    }

    @Operation(summary = "Xóa mềm kỹ năng dịch vụ", description = "ADMIN/STAFF; chặn khi staff có lịch được giao cho dịch vụ này từ hôm nay về sau. Không tự thay staff trong lịch.")
    @DeleteMapping("/{id}/skills/{serviceId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<Void> removeSkill(@PathVariable Long id, @PathVariable Long serviceId) {
        service.removeSkill(id, serviceId);
        return ApiResponse.success(null);
    }

    @Operation(summary = "Phân trang ca làm việc của chuyên viên", description = "Chỉ ADMIN/STAFF/RECEPTION; ngày từ/to bao gồm hai đầu, mặc định từ hôm nay trừ một tháng đến cộng ba tháng theo Việt Nam; ca chưa xóa. page/size/sort.")
    @GetMapping("/{id}/schedules")
    public ApiResponse<PageResponse<AdminStaffService.ScheduleView>> schedules(@PathVariable Long id, @Parameter(description = "Ngày bắt đầu yyyy-MM-dd, bao gồm ngày này", example = "2026-10-01") @RequestParam(required = false) LocalDate from, @Parameter(description = "Ngày kết thúc yyyy-MM-dd, không trước from", example = "2026-10-31") @RequestParam(required = false) LocalDate to, @ParameterObject Pageable pageable) {
        return ApiResponse.success(PageResponse.of(service.schedules(id, from, to, pageable)));
    }

    @Operation(summary = "Thêm ca làm việc cho chuyên viên", description = "ADMIN/STAFF; ngày/giờ Việt Nam, endTime>startTime trong cùng ngày. Không trùng ca, status thiếu/null là SCHEDULED. Ca hoạt động phải phủ đủ interval dịch vụ gồm chuẩn bị khi đặt/xác nhận.")
    @PostMapping("/{id}/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<AdminStaffService.ScheduleView> createSchedule(@PathVariable Long id, @RequestBody AdminStaffService.ScheduleCommand command) {
        return ApiResponse.created(service.saveSchedule(id, null, command), "Schedule created");
    }

    @Operation(summary = "Cập nhật ca làm việc của chuyên viên", description = "ADMIN/STAFF; scheduleId phải thuộc staff, chưa xóa. Chặn trùng ca và thay đổi khiến lịch đang được giao từ hôm nay về sau không còn ca phủ đủ. Toàn giao dịch rollback nếu không hợp lệ.")
    @PutMapping("/{id}/schedules/{scheduleId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<AdminStaffService.ScheduleView> updateSchedule(@PathVariable Long id, @PathVariable Long scheduleId, @RequestBody AdminStaffService.ScheduleCommand command) {
        return ApiResponse.success(service.saveSchedule(id, scheduleId, command));
    }

    @Operation(summary = "Xóa mềm ca làm việc của chuyên viên", description = "ADMIN/STAFF; scheduleId phải thuộc staff. Chặn khi xóa khiến lịch được giao từ hôm nay về sau mất ca phủ đầy đủ; không tự đổi giờ hoặc hủy lịch khách.")
    @DeleteMapping("/{id}/schedules/{scheduleId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<Void> deleteSchedule(@PathVariable Long id, @PathVariable Long scheduleId) {
        service.deleteSchedule(id, scheduleId);
        return ApiResponse.success(null);
    }
}
