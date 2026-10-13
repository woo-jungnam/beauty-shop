package com.core.beautyshop.modules.spa.api;

import com.core.beautyshop.modules.spa.application.service.SpaPreparationService;
import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.shared.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/spa/preparation")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
@Tag(name = "Biểu mẫu và chính sách chuẩn bị Spa (Admin)", description = "ADMIN/STAFF quản lý template/version bất biến và chính sách áp dụng cho booking mới")
public class AdminSpaPreparationController {
    private final SpaPreparationService preparation;

    @Operation(summary = "Phân trang các template form hiện hành", description = "Chỉ ADMIN; mỗi template trả version mới nhất. Dùng endpoint versions để xem các phiên bản cũ.")
    @GetMapping("/templates")
    public ApiResponse<PageResponse<SpaPreparationService.FormVersion>> templates(@Parameter(description = "Trang từ 0", schema = @Schema(minimum = "0")) @RequestParam(defaultValue="0") int page,
                                                                                 @Parameter(description = "Số bản ghi 1–100", schema = @Schema(minimum = "1", maximum = "100")) @RequestParam(defaultValue="20") int size) {
        return ApiResponse.success(preparation.templates(page, size));
    }

    @Operation(summary = "Tạo template và phiên bản form đầu tiên", description = "Chỉ ADMIN; 1–50 câu hỏi TEXT/BOOLEAN, key duy nhất, title và labels hợp lệ. Trả HTTP 201. Không tự gắn template vào mọi dịch vụ hoặc booking.")
    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SpaPreparationService.FormVersion> create(@RequestBody SpaPreparationService.FormCommand command) {
        return ApiResponse.created(preparation.createTemplate(command), "Form template created");
    }

    @Operation(summary = "Tạo phiên bản form mới", description = "Chỉ ADMIN; append một version bất biến, không sửa version cũ hoặc yêu cầu đã chụp trên lịch. Chính sách service phải chọn version mới nếu muốn áp dụng cho booking mới.")
    @PostMapping("/templates/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SpaPreparationService.FormVersion> version(@PathVariable Long id, @RequestBody SpaPreparationService.FormCommand command) {
        return ApiResponse.created(preparation.addVersion(id, command), "New immutable form version created");
    }

    @Operation(summary = "Xem lịch sử phiên bản của template", description = "Chỉ ADMIN; danh sách version theo số phiên bản giảm dần, không phân trang.")
    @GetMapping("/templates/{id}/versions")
    public ApiResponse<List<SpaPreparationService.FormVersion>> versions(@PathVariable Long id) {
        return ApiResponse.success(preparation.versions(id));
    }

    @Operation(summary = "Xem yêu cầu form/cảnh báo của dịch vụ", description = "Chỉ ADMIN; trả cấu hình hiện tại dùng cho booking mới. Cấu hình mặc định là không form, warningsRequired=false.")
    @GetMapping("/services/{id}")
    public ApiResponse<SpaPreparationService.ServicePolicy> policy(@PathVariable Long id) {
        return ApiResponse.success(preparation.policy(id));
    }

    @Operation(summary = "Thay chính sách chuẩn bị cho booking mới", description = "Chỉ ADMIN; requiredFormVersionIds là danh sách thay toàn bộ, tối đa 20 version và một version mỗi template; [] bỏ form bắt buộc cho booking mới. Không sửa snapshot hoặc consent của lịch đã đặt.")
    @PutMapping("/services/{id}")
    public ApiResponse<SpaPreparationService.ServicePolicy> policy(@PathVariable Long id, @RequestBody SpaPreparationService.ServicePolicyCommand command) {
        return ApiResponse.success(preparation.configureService(id, command));
    }
}
