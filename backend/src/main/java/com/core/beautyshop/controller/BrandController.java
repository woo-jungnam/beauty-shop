package com.core.beautyshop.controller;

import io.swagger.v3.oas.annotations.tags.Tag;


import com.core.beautyshop.dto.common.ApiResponse;
import com.core.beautyshop.dto.common.PageResponse;
import com.core.beautyshop.dto.request.CreateBrandRequest;
import com.core.beautyshop.dto.request.UpdateBrandRequest;
import com.core.beautyshop.dto.response.BrandResponse;
import com.core.beautyshop.service.brand.BrandService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest req) {
        Page<BrandResponse> page = brandService.getAllBrands(pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(page), req.getRequestURI()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BrandResponse>> getBrandById(
            @PathVariable Long id,
            HttpServletRequest req) {
        BrandResponse brand = brandService.getBrandById(id);
        return ResponseEntity.ok(ApiResponse.success(brand, req.getRequestURI()));
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<ApiResponse<BrandResponse>> getBrandBySlug(
            @PathVariable String slug,
            HttpServletRequest req) {
        BrandResponse brand = brandService.getBrandBySlug(slug);
        return ResponseEntity.ok(ApiResponse.success(brand, req.getRequestURI()));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BrandResponse>> createBrand(
            @Valid @RequestBody CreateBrandRequest request,
            HttpServletRequest req) {
        BrandResponse brand = brandService.createBrand(request);
        return ResponseEntity.status(201).body(ApiResponse.created(brand, "Brand created successfully", req.getRequestURI()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BrandResponse>> updateBrand(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBrandRequest request,
            HttpServletRequest req) {
        BrandResponse brand = brandService.updateBrand(id, request);
        return ResponseEntity.ok(ApiResponse.success(brand, req.getRequestURI()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteBrand(
            @PathVariable Long id,
            HttpServletRequest req) {
        brandService.deleteBrand(id);
        return ResponseEntity.ok(ApiResponse.success(null, req.getRequestURI()));
    }
}
