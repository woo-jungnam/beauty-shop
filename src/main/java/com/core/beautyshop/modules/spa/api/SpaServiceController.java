package com.core.beautyshop.modules.spa.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.format.annotation.DateTimeFormat;

import com.core.beautyshop.modules.spa.application.dto.response.BeautyServiceResponse;
import com.core.beautyshop.modules.spa.application.service.BeautyServiceService;
import com.core.beautyshop.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Dịch vụ Spa", description = "API công khai cho các dịch vụ Spa và Làm đẹp")
@RestController
@RequestMapping("/api/v1/spa/services")
@RequiredArgsConstructor
public class SpaServiceController {

    private final BeautyServiceService beautyServiceService;

    @Operation(summary = "Lấy danh sách dịch vụ Spa đang hoạt động", description = "API công khai; chỉ dịch vụ hoạt động, chưa xóa. Không phân trang.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<BeautyServiceResponse>>> getAllServices() {
        return ResponseEntity.ok(ApiResponse.success(
                beautyServiceService.getAllActiveServices()
        ));
    }

    @Operation(summary = "Xem chi tiết dịch vụ Spa theo ID", description = "API công khai; chỉ dịch vụ hoạt động, chưa xóa. Dịch vụ ngưng hoặc đã xóa trả không tìm thấy.")
    @GetMapping("/{id:[0-9]+}")
    public ResponseEntity<ApiResponse<BeautyServiceResponse>> getServiceById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                beautyServiceService.getServiceById(id)
        ));
    }

    @Operation(summary = "Xem chi tiết dịch vụ Spa theo slug", description = "API công khai; chỉ dịch vụ hoạt động, chưa xóa.")
    @GetMapping("/slug/{slug}")
    public ResponseEntity<ApiResponse<BeautyServiceResponse>> getServiceBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(
                beautyServiceService.getServiceBySlug(slug)
        ));
    }

    @Operation(summary = "Lấy danh sách gói Spa có thể mua", description = "API công khai; chỉ gói hoạt động, chưa xóa và có các dịch vụ thành phần còn khả dụng. Danh sách không phân trang; không cấp vé ở bước đọc catalog.")
    @GetMapping("/packages")
    public ResponseEntity<ApiResponse<List<com.core.beautyshop.modules.spa.application.dto.response.ServicePackageResponse>>> getPackages() {
        return ResponseEntity.ok(ApiResponse.success(beautyServiceService.getActivePackages()));
    }

    @Operation(summary = "Lấy tất cả nhân viên Spa đang hoạt động", description = "API công khai; chỉ nhân viên hoạt động, chưa xóa.")
    @GetMapping("/staff")
    public ResponseEntity<ApiResponse<List<com.core.beautyshop.modules.spa.application.dto.response.StaffResponse>>> getAllStaff() {
        return ResponseEntity.ok(ApiResponse.success(beautyServiceService.getAllStaff()));
    }

    @Operation(summary = "Lấy nhân viên có kỹ năng cho dịch vụ", description = "API công khai; dịch vụ hoạt động/chưa xóa, staff hoạt động/chưa xóa và có kỹ năng dịch vụ. Danh sách này không chứng minh nhân viên rảnh vào một giờ cụ thể; dùng available-slots để kiểm ca và slot.")
    @GetMapping("/{id}/staff")
    public ResponseEntity<ApiResponse<List<com.core.beautyshop.modules.spa.application.dto.response.StaffResponse>>> getQualifiedStaff(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(beautyServiceService.getQualifiedStaff(id)));
    }

    @Operation(summary = "Gợi ý giờ trống cho một dịch vụ", description = "API công khai; trả giờ địa phương Việt Nam theo bước 30 phút trong giờ mở cửa, tính cả thời gian chuẩn bị. Kiểm ca/kỹ năng nhân viên, planned overlap và tài nguyên cấu hình. Bỏ staffId nghĩa là có ít nhất một staff phù hợp. Đây là gợi ý tại thời điểm đọc, không giữ chỗ; booking kiểm lại dưới khóa.")
    @GetMapping("/{id}/available-slots")
    public ResponseEntity<ApiResponse<List<String>>> getAvailableSlots(
            @PathVariable Long id,
            @Parameter(description = "Ngày từ hôm nay theo Asia/Ho_Chi_Minh, yyyy-MM-dd", example = "2026-10-05") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate date,
            @Parameter(description = "ID hồ sơ Staff có kỹ năng dịch vụ, không phải ID User; bỏ trống để tìm bất kỳ staff phù hợp") @RequestParam(required = false) Long staffId) {
        return ResponseEntity.ok(ApiResponse.success(beautyServiceService.getAvailableSlots(id, date, staffId)));
    }
}
