package com.core.beautyshop.modules.spa.api;
import com.core.beautyshop.modules.spa.application.service.SpaConsumableService;
import com.core.beautyshop.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
@Tag(name = "Vật tư & Tiêu hao Spa", description = "Định mức vật tư dịch vụ, ghi nhận tiêu hao thực tế và miễn trừ tiêu hao cho buổi Spa")
public class SpaConsumableController {
    private final SpaConsumableService materials;

    @Operation(summary = "Danh mục vật tư tiêu hao có thể sử dụng", description = "Lấy danh sách các vật tư, mỹ phẩm trong kho khả dụng cho buổi làm dịch vụ Spa")
    @PreAuthorize("@staffPermissions.has('SPA_EXECUTION') or @staffPermissions.has('SPA_RECEPTION')")
    @GetMapping("/api/v1/spa/material-options") public ApiResponse<List<com.core.beautyshop.modules.inventory.api.dto.SpaMaterialOption>> options() { return ApiResponse.success(materials.options()); }

    @Operation(summary = "Xem định mức vật tư tiêu hao theo dịch vụ (Admin)", description = "Xem định lượng vật tư, thuốc, mỹ phẩm tiêu chuẩn cần dùng cho một lượt dịch vụ")
    @GetMapping("/api/v1/admin/spa/services/{id}/materials") public ApiResponse<List<SpaConsumableService.Material>> definitions(@PathVariable Long id) { return ApiResponse.success(materials.definitions(id)); }

    @Operation(summary = "Cập nhật định mức vật tư tiêu hao (Admin)", description = "Cấu hình lại danh sách và định lượng vật tư tiêu chuẩn cho dịch vụ Spa")
    @PutMapping("/api/v1/admin/spa/services/{id}/materials") public ApiResponse<List<SpaConsumableService.Material>> save(@PathVariable Long id,@RequestBody List<SpaConsumableService.Material> rows) { return ApiResponse.success(materials.saveDefinitions(id,rows)); }

    @Operation(summary = "Xem vật tư đã sử dụng trong lịch hẹn", description = "Xem chi tiết tiêu hao thực tế của từng dịch vụ trong buổi hẹn Spa")
    @GetMapping("/api/v1/spa/appointments/{id}/materials") public ApiResponse<List<SpaConsumableService.ItemMaterials>> view(@PathVariable Long id) { return ApiResponse.success(materials.view(id)); }

    @Operation(summary = "Ghi nhận tiêu hao vật tư thực tế (Staff/Admin)", description = "Chuyên viên ghi nhận lượng vật tư, mỹ phẩm thực tế đã dùng để trừ tồn kho")
    @PostMapping("/api/v1/spa/appointments/{id}/items/{itemId}/material-usages") public ApiResponse<List<SpaConsumableService.ItemMaterials>> consume(@PathVariable Long id,@PathVariable Long itemId,@RequestBody SpaConsumableService.UsageCommand c,@RequestHeader("Idempotency-Key") String key) { return ApiResponse.success(materials.consume(id,itemId,c,key)); }

    public record Waiver(String reason) {}

    @Operation(summary = "Miễn trừ tiêu hao vật tư (Staff/Admin)", description = "Xác nhận không sử dụng vật tư tiêu hao kèm theo lý do cụ thể")
    @PostMapping("/api/v1/spa/appointments/{id}/items/{itemId}/material-waiver") public ApiResponse<List<SpaConsumableService.ItemMaterials>> waive(@PathVariable Long id,@PathVariable Long itemId,@RequestBody Waiver c) { return ApiResponse.success(materials.waive(id,itemId,c.reason())); }
}
