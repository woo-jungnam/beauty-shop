package com.core.beautyshop.modules.spa.api;

import com.core.beautyshop.modules.spa.application.service.AdminFacilityService;
import com.core.beautyshop.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.List;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin/spa/facilities")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')") @Tag(name = "Tài nguyên Spa (Admin)", description = "Quản lý phòng/máy, capacity và trạng thái sử dụng")
public class AdminFacilityController {
    private final AdminFacilityService service;
    @Operation(summary = "Danh sách tài nguyên Spa chưa xóa", description = "Admin hoặc nhân viên Spa; gồm active và inactive, theo ID tăng dần, không phân trang.")
    @GetMapping public ApiResponse<List<AdminFacilityService.FacilityView>> list() { return ApiResponse.success(service.list()); }
    @Operation(summary = "Xem chi tiết tài nguyên Spa", description = "Admin hoặc nhân viên Spa; tài nguyên đã soft-delete trả không tìm thấy.")
    @GetMapping("/{id}") public ApiResponse<AdminFacilityService.FacilityView> get(@PathVariable Long id) { return ApiResponse.success(service.get(id)); }
    @Operation(summary = "Tạo giường, phòng hoặc thiết bị", description = "Admin hoặc nhân viên Spa; name bắt buộc, type được chuẩn hóa chữ hoa, capacity dương.")
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<AdminFacilityService.FacilityView> create(@RequestBody AdminFacilityService.FacilityCommand command) { return ApiResponse.created(service.save(null, command), "Facility created"); }
    @Operation(summary = "Cập nhật tài nguyên Spa", description = "ADMIN hoặc STAFF; name bắt buộc.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<AdminFacilityService.FacilityView> update(@PathVariable Long id, @RequestBody AdminFacilityService.FacilityCommand command) { return ApiResponse.success(service.save(id, command)); }
    @Operation(summary = "Xóa mềm tài nguyên Spa", description = "Admin hoặc nhân viên Spa; chặn nếu có lịch còn giữ tài nguyên.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<Void> delete(@PathVariable Long id) { service.delete(id); return ApiResponse.success(null); }
    @Operation(summary = "Xem các khoảng bảo trì của tài nguyên", description = "Admin hoặc nhân viên Spa; block chưa xóa.")
    @GetMapping("/{id}/blocks") public ApiResponse<List<AdminFacilityService.BlockView>> blocks(@PathVariable Long id) { return ApiResponse.success(service.blocks(id)); }
    @Operation(summary = "Tạo khoảng ngừng phục vụ tài nguyên", description = "Admin hoặc nhân viên Spa; startAt/endAt là ngày giờ Spa tại Việt Nam, end>start, reason bắt buộc.")
    @PostMapping("/{id}/blocks") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<AdminFacilityService.BlockView> createBlock(@PathVariable Long id, @RequestBody AdminFacilityService.BlockCommand command) { return ApiResponse.created(service.saveBlock(id, null, command), "Facility block created"); }
    @Operation(summary = "Đổi khoảng bảo trì tài nguyên", description = "Admin hoặc nhân viên Spa; block phải thuộc facility trong đường dẫn.")
    @PutMapping("/{id}/blocks/{blockId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<AdminFacilityService.BlockView> updateBlock(@PathVariable Long id, @PathVariable Long blockId, @RequestBody AdminFacilityService.BlockCommand command) { return ApiResponse.success(service.saveBlock(id, blockId, command)); }
    @Operation(summary = "Xóa mềm khoảng bảo trì", description = "Admin hoặc nhân viên Spa; block phải thuộc facility trong đường dẫn.")
    @DeleteMapping("/{id}/blocks/{blockId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<Void> deleteBlock(@PathVariable Long id, @PathVariable Long blockId) { service.deleteBlock(id, blockId); return ApiResponse.success(null); }
}
