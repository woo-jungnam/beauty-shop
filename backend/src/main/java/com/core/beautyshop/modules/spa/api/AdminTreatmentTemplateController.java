package com.core.beautyshop.modules.spa.api;
import com.core.beautyshop.modules.spa.application.service.TreatmentCourseService;
import com.core.beautyshop.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import static com.core.beautyshop.modules.spa.application.service.TreatmentContracts.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin/spa/treatment-templates")
@PreAuthorize("hasRole('ADMIN')")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Treatment template administration")
public class AdminTreatmentTemplateController {
    private final TreatmentCourseService treatments;
    @Operation(summary = "Quản trị xem mẫu lộ trình và các bản nháp")
    @GetMapping public ApiResponse<List<TemplateView>> list() { return ApiResponse.success(treatments.templates(true)); }
    @Operation(summary = "Xem bản nháp hoặc phiên bản hiện tại của mẫu")
    @GetMapping("/{id}") public ApiResponse<TemplateView> detail(@PathVariable Long id) { return ApiResponse.success(treatments.template(id, true)); }
    @Operation(summary = "Tạo mẫu lộ trình gồm từng buổi và dịch vụ thực hiện")
    @PostMapping public ApiResponse<TemplateView> create(@RequestBody Plan command) { return ApiResponse.success(treatments.saveTemplate(null, command)); }
    @Operation(summary = "Sửa bản nháp, không thay đổi phiên bản đã xuất bản")
    @PutMapping("/{id}") public ApiResponse<TemplateView> update(@PathVariable Long id, @RequestBody Plan command) { return ApiResponse.success(treatments.saveTemplate(id, command)); }
    @Operation(summary = "Tạo phiên bản nháp mới từ mẫu đã xuất bản")
    @PostMapping("/{id}/versions") public ApiResponse<TemplateView> version(@PathVariable Long id, @RequestBody(required = false) Plan command) { return ApiResponse.success(treatments.newVersion(id, command)); }
    @Operation(summary = "Xuất bản phiên bản mới cho các lượt mua tiếp theo")
    @PostMapping("/{id}/publish") public ApiResponse<TemplateView> publish(@PathVariable Long id) { return ApiResponse.success(treatments.publish(id)); }
    @Operation(summary = "Ngừng bán mẫu, giữ nguyên hồ sơ của khách đã mua")
    @PostMapping("/{id}/archive") public ApiResponse<TemplateView> archive(@PathVariable Long id) { return ApiResponse.success(treatments.archive(id)); }
}
