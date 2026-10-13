package com.core.beautyshop.modules.catalog.api;

import com.core.beautyshop.modules.catalog.application.service.AdminIngredientService;
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

@Tag(name = "Quản lý hoạt chất mỹ phẩm (Admin)", description = "API dành cho ADMIN hoặc STAFF quản lý hoạt chất INCI và mapping sản phẩm")
@RestController
@RequestMapping("/api/v1/admin/ingredients")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
@RequiredArgsConstructor
public class AdminIngredientController {
    private final AdminIngredientService service;

    @Operation(summary = "Lấy danh sách hoạt chất (phân trang)")
    @GetMapping
    public ApiResponse<PageResponse<AdminIngredientService.IngredientView>> list(@ParameterObject Pageable pageable) {
        return ApiResponse.success(PageResponse.of(service.list(pageable)));
    }

    @Operation(summary = "Xem chi tiết hoạt chất theo ID")
    @GetMapping("/{id}")
    public ApiResponse<AdminIngredientService.IngredientView> getById(@PathVariable Long id) {
        return ApiResponse.success(service.get(id));
    }

    @Operation(summary = "Tạo hoạt chất mỹ phẩm mới")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminIngredientService.IngredientView> create(@RequestBody AdminIngredientService.IngredientCommand command) {
        return ApiResponse.created(service.save(null, command), "Ingredient created");
    }

    @Operation(summary = "Cập nhật thông tin hoạt chất")
    @PutMapping("/{id}")
    public ApiResponse<AdminIngredientService.IngredientView> update(@PathVariable Long id, @RequestBody AdminIngredientService.IngredientCommand command) {
        return ApiResponse.success(service.save(id, command));
    }

    @Operation(summary = "Xóa hoạt chất mỹ phẩm")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }

    @Operation(summary = "Lấy danh sách hoạt chất trong sản phẩm")
    @GetMapping("/products/{productId}")
    public ApiResponse<List<AdminIngredientService.ProductIngredientView>> mappings(@PathVariable Long productId) {
        return ApiResponse.success(service.productIngredients(productId));
    }

    @Operation(summary = "Thay toàn bộ danh sách hoạt chất cho sản phẩm", description = "ADMIN hoặc CATALOG_STAFF. Body là mảng mapping; [] gỡ tất cả. ingredientId phải tồn tại/chưa xóa và không trùng; concentration không âm. Đây là thay thế hoàn toàn, không merge.")
    @PutMapping("/products/{productId}")
    public ApiResponse<List<AdminIngredientService.ProductIngredientView>> replace(@PathVariable Long productId, @RequestBody List<AdminIngredientService.ProductIngredientCommand> commands) {
        return ApiResponse.success(service.replaceProductIngredients(productId, commands));
    }
}
