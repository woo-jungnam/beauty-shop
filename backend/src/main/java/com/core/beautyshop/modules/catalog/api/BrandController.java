package com.core.beautyshop.modules.catalog.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.shared.dto.PageResponse;
import com.core.beautyshop.modules.catalog.application.dto.request.CreateBrandRequest;
import com.core.beautyshop.modules.catalog.application.dto.request.UpdateBrandRequest;
import com.core.beautyshop.modules.catalog.application.dto.response.BrandResponse;
import com.core.beautyshop.modules.catalog.application.service.BrandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Quản lý thương hiệu", description = "Các API thao tác danh mục thương hiệu sản phẩm")
@RestController
@RequestMapping("/api/v1/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    @Operation(summary = "Lấy danh sách tất cả thương hiệu (phân trang)", description = "Trả về danh sách thương hiệu hỗ trợ phân trang qua page, size, sort.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<BrandResponse>>> getAllBrands(
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        Page<BrandResponse> page = brandService.getAllBrands(pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(page)));
    }

    @Operation(summary = "Xem chi tiết thương hiệu theo ID", description = "Tìm kiếm thông tin chi tiết một thương hiệu theo mã ID.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BrandResponse>> getBrandById(
            @Parameter(description = "ID thương hiệu", example = "1") @PathVariable Long id) {
        BrandResponse brand = brandService.getBrandById(id);
        return ResponseEntity.ok(ApiResponse.success(brand));
    }

    @Operation(summary = "Xem chi tiết thương hiệu theo Slug", description = "Dùng cho Frontend routing trang chi tiết thương hiệu theo URL thân thiện SEO.")
    @GetMapping("/slug/{slug}")
    public ResponseEntity<ApiResponse<BrandResponse>> getBrandBySlug(
            @Parameter(description = "Đường dẫn slug của thương hiệu", example = "la-roche-posay") @PathVariable String slug) {
        BrandResponse brand = brandService.getBrandBySlug(slug);
        return ResponseEntity.ok(ApiResponse.success(brand));
    }

    @Operation(summary = "Tạo thương hiệu mới (Admin)", description = "Yêu cầu quyền ADMIN. Slug không được trùng lặp.")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BrandResponse>> createBrand(
            @Valid @RequestBody CreateBrandRequest request) {
        BrandResponse brand = brandService.createBrand(request);
        return ResponseEntity.status(201).body(ApiResponse.created(brand, "Tạo thương hiệu thành công"));
    }

    @Operation(summary = "Cập nhật thông tin thương hiệu (Admin)", description = "Yêu cầu quyền ADMIN.")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BrandResponse>> updateBrand(
            @Parameter(description = "ID thương hiệu cần cập nhật", example = "1") @PathVariable Long id,
            @Valid @RequestBody UpdateBrandRequest request) {
        BrandResponse brand = brandService.updateBrand(id, request);
        return ResponseEntity.ok(ApiResponse.success(brand));
    }

    @Operation(summary = "Xóa thương hiệu (Admin)", description = "Yêu cầu quyền ADMIN.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteBrand(
            @Parameter(description = "ID thương hiệu cần xóa", example = "1") @PathVariable Long id) {
        brandService.deleteBrand(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
