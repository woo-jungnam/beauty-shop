package com.core.beautyshop.modules.spa.api;

import com.core.beautyshop.modules.spa.application.dto.response.*;
import com.core.beautyshop.modules.spa.application.service.AdminSpaCatalogService;
import com.core.beautyshop.shared.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Dịch vụ Spa, Danh mục & Gói liệu trình (Admin)", description = "ADMIN và STAFF quản lý nghiệp vụ dịch vụ; gồm bản ghi inactive chưa xóa. Thay catalog không viết lại snapshot lịch hoặc quyền gói đã mua")
@RestController
@RequestMapping("/api/v1/admin/spa")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
@RequiredArgsConstructor
public class AdminSpaServiceController {
    private final AdminSpaCatalogService service;

    @Operation(summary = "Lấy danh sách dịch vụ Spa (phân trang)", description = "ADMIN/STAFF; mọi dịch vụ chưa xóa, gồm inactive. Pageable dùng page/size/sort.")
    @GetMapping("/services")
    public ApiResponse<PageResponse<BeautyServiceResponse>> services(@ParameterObject Pageable pageable) {
        return ApiResponse.success(PageResponse.of(service.services(pageable)));
    }

    @Operation(summary = "Xem chi tiết dịch vụ Spa theo ID", description = "ADMIN/STAFF; xem được inactive, không xem bản ghi đã xóa.")
    @GetMapping("/services/{id}")
    public ApiResponse<BeautyServiceResponse> getService(@PathVariable Long id) {
        return ApiResponse.success(service.service(id));
    }

    @Operation(summary = "Tạo dịch vụ Spa mới", description = "ADMIN/STAFF; name/slug/price/duration bắt buộc, price>=0, duration>0; preparation mặc định 15 phút, tổng duration+preparation<=720 phút. active thiếu/null là true. Không tự cấu hình resource hoặc form.")
    @PostMapping("/services")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BeautyServiceResponse> createService(@RequestBody AdminSpaCatalogService.ServiceCommand command) {
        return ApiResponse.created(service.saveService(null, command), "Spa service created");
    }

    @Operation(summary = "Cập nhật dịch vụ Spa", description = "ADMIN/STAFF; cập nhật các trường catalog, active thiếu/null là true. Giá/tên/thời lượng đã chụp ở lịch cũ giữ nguyên. Chặn active=false khi còn lịch chưa kết thúc, quyền vé chưa hết hạn hoặc đơn gói chưa issue; không tự remap dịch vụ/hoàn tiền.")
    @PutMapping("/services/{id}")
    public ApiResponse<BeautyServiceResponse> updateService(@PathVariable Long id, @RequestBody AdminSpaCatalogService.ServiceCommand command) {
        return ApiResponse.success(service.saveService(id, command));
    }

    @Operation(summary = "Xóa mềm dịch vụ Spa", description = "ADMIN/STAFF; cùng guard nghĩa vụ như ngưng dịch vụ, kể cả lịch đã qua ngày nhưng chưa kết thúc. Không tự hủy lịch hoặc hoàn gói.")
    @DeleteMapping("/services/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<Void> deleteService(@PathVariable Long id) {
        service.deleteService(id);
        return ApiResponse.success(null);
    }

    @Operation(summary = "Lấy danh sách danh mục Spa (phân trang)", description = "ADMIN/STAFF; danh mục chưa xóa, gồm inactive. page/size/sort.")
    @GetMapping("/categories")
    public ApiResponse<PageResponse<AdminSpaCatalogService.CategoryView>> categories(@ParameterObject Pageable pageable) {
        return ApiResponse.success(PageResponse.of(service.categories(pageable)));
    }

    @Operation(summary = "Xem chi tiết danh mục Spa theo ID", description = "ADMIN/STAFF; không trả danh mục đã xóa.")
    @GetMapping("/categories/{id}")
    public ApiResponse<AdminSpaCatalogService.CategoryView> getCategory(@PathVariable Long id) {
        return ApiResponse.success(service.category(id));
    }

    @Operation(summary = "Tạo danh mục Spa mới", description = "ADMIN/STAFF; name/slug không trắng, slug không trùng; active thiếu/null là true.")
    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminSpaCatalogService.CategoryView> createCategory(@RequestBody AdminSpaCatalogService.CategoryCommand command) {
        return ApiResponse.created(service.saveCategory(null, command), "Spa category created");
    }

    @Operation(summary = "Cập nhật danh mục Spa", description = "ADMIN/STAFF; cập nhật name/slug/description/thumbnail/active. active thiếu/null là true. Không thay yêu cầu form/resource của dịch vụ.")
    @PutMapping("/categories/{id}")
    public ApiResponse<AdminSpaCatalogService.CategoryView> updateCategory(@PathVariable Long id, @RequestBody AdminSpaCatalogService.CategoryCommand command) {
        return ApiResponse.success(service.saveCategory(id, command));
    }

    @Operation(summary = "Xóa mềm danh mục Spa", description = "ADMIN/STAFF; ngưng danh mục, không xóa hoặc ngưng tự động các dịch vụ liên kết.")
    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        service.deleteCategory(id);
        return ApiResponse.success(null);
    }

    @Operation(summary = "Lấy danh sách gói liệu trình Spa", description = "ADMIN/STAFF; gói chưa xóa gồm inactive và các item, danh sách không phân trang.")
    @GetMapping("/packages")
    public ApiResponse<List<ServicePackageResponse>> packages() {
        return ApiResponse.success(service.packages());
    }

    @Operation(summary = "Xem chi tiết gói liệu trình Spa theo ID", description = "ADMIN/STAFF; gói chưa xóa. Quyền lợi đã mua phải đọc snapshot/ticket, không suy lại từ cấu hình catalog hiện tại.")
    @GetMapping("/packages/{id}")
    public ApiResponse<ServicePackageResponse> getPackage(@PathVariable Long id) {
        return ApiResponse.success(service.packageDetail(id));
    }

    @Operation(summary = "Tạo gói liệu trình Spa mới", description = "ADMIN/STAFF; price>=1 VND, items không rỗng, serviceId không trùng và quantity>0; mỗi dịch vụ phải còn hoạt động/chưa xóa. validityDays dương hoặc null không hết hạn; active thiếu/null là true.")
    @PostMapping("/packages")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ServicePackageResponse> createPackage(@RequestBody AdminSpaCatalogService.PackageCommand command) {
        return ApiResponse.created(service.savePackage(null, command), "Spa package created");
    }

    @Operation(summary = "Cập nhật gói liệu trình Spa", description = "ADMIN/STAFF; thay toàn bộ danh sách items và các trường catalog. Purchase snapshot/ticket đã cấp không đổi theo giá/quota/validity mới. active thiếu/null là true.")
    @PutMapping("/packages/{id}")
    public ApiResponse<ServicePackageResponse> updatePackage(@PathVariable Long id, @RequestBody AdminSpaCatalogService.PackageCommand command) {
        return ApiResponse.success(service.savePackage(id, command));
    }

    @Operation(summary = "Xóa mềm gói liệu trình Spa", description = "ADMIN/STAFF; ngưng mua gói mới, không thu hồi vé đã mua hoặc tự hoàn tiền.")
    @DeleteMapping("/packages/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ApiResponse<Void> deletePackage(@PathVariable Long id) {
        service.deletePackage(id);
        return ApiResponse.success(null);
    }
}
