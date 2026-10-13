package com.core.beautyshop.shared.config.api;

import com.core.beautyshop.shared.config.domain.SystemConfig;
import com.core.beautyshop.shared.config.domain.SystemConfigRepository;
import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Cấu hình hệ thống & Mục tiêu KPI (Admin)", description = "ADMIN quản lý cấu hình nghiệp vụ lưu trong database: KPI, cảnh báo tồn và policy Spa. Không chỉnh biến môi trường, JWT secret hoặc SMTP qua API này.")
@RestController
@RequestMapping("/api/v1/admin/system-configs")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSystemConfigController {
    private final SystemConfigRepository repository;
    private final com.core.beautyshop.shared.config.SystemConfigService configService;

    @Operation(summary = "Lấy toàn bộ danh sách cấu hình hệ thống")
    @GetMapping
    public ApiResponse<List<SystemConfig>> list() {
        return ApiResponse.success(repository.findAll().stream().filter(config -> !Boolean.TRUE.equals(config.getIsDeleted())).toList());
    }

    @Operation(summary = "Xem chi tiết một cấu hình hệ thống theo Key")
    @GetMapping("/{key}")
    public ApiResponse<SystemConfig> getByKey(@PathVariable String key) {
        SystemConfig config = repository.findByConfigKeyAndIsDeletedFalse(key)
                .orElseThrow(() -> new ResourceNotFoundException("System config not found: " + key));
        return ApiResponse.success(config);
    }

    @Operation(summary = "Tạo cấu hình nghiệp vụ", description = "HTTP 201. Key đã tồn tại chưa xóa bị từ chối; key đã soft-delete được khôi phục. Các key chuẩn yêu cầu đúng valueType và khoảng giá trị. Policy mới chỉ áp lên booking/purchase mới theo cơ chế snapshot.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SystemConfig> create(@RequestBody CreateConfigRequest request) {
        return ApiResponse.created(configService.create(request.key(), request.value(), request.description(), request.valueType()), "System config created");
    }

    @Operation(summary = "Cập nhật giá trị cấu hình", description = "Chỉ thay value của key đang hoạt động; không thay valueType qua endpoint này. Các policy đã chụp trong booking/ticket được giữ nguyên.")
    @PutMapping("/{key}")
    public ApiResponse<SystemConfig> update(@PathVariable String key, @RequestBody ConfigRequest request) {
        return ApiResponse.success(configService.update(key, request.value()));
    }

    @Operation(summary = "Ngừng sử dụng cấu hình", description = "Soft-delete. Những bộ đọc có giá trị mặc định sẽ dùng lại mặc định; thao tác không sửa các snapshot đã lưu.")
    @DeleteMapping("/{key}")
    public ApiResponse<Void> delete(@PathVariable String key) {
        configService.delete(key);
        return ApiResponse.success(null);
    }

    public record ConfigRequest(
            @io.swagger.v3.oas.annotations.media.Schema(description = "Giá trị dưới dạng chuỗi, theo kiểu cấu hình hiện có", example = "30", requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED) String value) { }
    public record CreateConfigRequest(
            @io.swagger.v3.oas.annotations.media.Schema(description = "Key duy nhất; trim, tối đa 100 ký tự", example = "spa.booking.pending_ttl_minutes", requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED) String key,
            @io.swagger.v3.oas.annotations.media.Schema(description = "Chuỗi không rỗng. BOOLEAN là true/false; JSON phải parse được. Các giá trị số chuẩn không âm; phút policy Spa tối đa 525600", example = "30", requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED) String value,
            @io.swagger.v3.oas.annotations.media.Schema(description = "Mô tả, tối đa 500 ký tự") String description,
            @io.swagger.v3.oas.annotations.media.Schema(description = "Nếu bỏ trống, suy từ key chuẩn hoặc dùng STRING", allowableValues = {"STRING","INTEGER","DECIMAL","BOOLEAN","JSON"}, example = "INTEGER") String valueType) { }
}
