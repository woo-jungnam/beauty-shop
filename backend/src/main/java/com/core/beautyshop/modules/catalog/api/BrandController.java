package com.core.beautyshop.modules.catalog.api;

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
@Tag(name = "Quản lý thương hiệu", description = "API quản lý thương hiệu")
@RestController
@RequestMapping("/api/v1/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<BrandResponse>>> getAllBrands(
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        Page<BrandResponse> page = brandService.getAllBrands(pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(page)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BrandResponse>> getBrandById(@PathVariable Long id) {
        BrandResponse brand = brandService.getBrandById(id);
        return ResponseEntity.ok(ApiResponse.success(brand));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<BrandResponse>> getBrandBySlug(@PathVariable String slug) {
        BrandResponse brand = brandService.getBrandBySlug(slug);
        return ResponseEntity.ok(ApiResponse.success(brand));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BrandResponse>> createBrand(
            @Valid @RequestBody CreateBrandRequest request) {
        BrandResponse brand = brandService.createBrand(request);
        return ResponseEntity.status(201).body(ApiResponse.created(brand, "Brand created successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BrandResponse>> updateBrand(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBrandRequest request) {
        BrandResponse brand = brandService.updateBrand(id, request);
        return ResponseEntity.ok(ApiResponse.success(brand));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteBrand(@PathVariable Long id) {
        brandService.deleteBrand(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
