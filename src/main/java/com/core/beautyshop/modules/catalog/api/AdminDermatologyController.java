package com.core.beautyshop.modules.catalog.api;

import com.core.beautyshop.modules.catalog.application.dto.request.ProductSkinCompatibilityItemRequest;
import com.core.beautyshop.modules.catalog.application.dto.request.ProductSkinConcernItemRequest;
import com.core.beautyshop.modules.catalog.application.dto.request.UpdateProductUsageRequest;
import com.core.beautyshop.modules.catalog.application.dto.response.ProductResponse;
import com.core.beautyshop.modules.catalog.application.service.AdminDermatologyService;
import com.core.beautyshop.modules.catalog.domain.SkinConcern;
import com.core.beautyshop.modules.catalog.domain.SkinTypeEntity;
import com.core.beautyshop.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Quản lý Dược mỹ phẩm & Routine da liễu (Admin)", description = "API quản trị HDSD, tương thích loại da và vấn đề da cho sản phẩm")
@RestController
@RequestMapping("/api/v1/admin/dermatology")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
@RequiredArgsConstructor
public class AdminDermatologyController {

    private final AdminDermatologyService dermatologyService;

    @Operation(summary = "Lấy danh mục chuẩn các loại da")
    @GetMapping("/skin-types")
    public ApiResponse<List<SkinTypeEntity>> getSkinTypes() {
        return ApiResponse.success(dermatologyService.getAllSkinTypes());
    }

    @Operation(summary = "Lấy danh mục chuẩn các vấn đề da liễu")
    @GetMapping("/skin-concerns")
    public ApiResponse<List<SkinConcern>> getSkinConcerns() {
        return ApiResponse.success(dermatologyService.getAllSkinConcerns());
    }

    @Operation(summary = "Lấy toàn bộ hồ sơ da liễu và quy trình sử dụng của sản phẩm")
    @GetMapping("/products/{productId}")
    public ApiResponse<AdminDermatologyService.ProductDermatologyProfileView> getProductProfile(@PathVariable Long productId) {
        return ApiResponse.success(dermatologyService.getProfile(productId));
    }

    @Operation(summary = "Cập nhật quy trình HDSD và cảnh báo (Usage Detail)")
    @PutMapping("/products/{productId}/usage")
    public ApiResponse<ProductResponse.UsageDetailResponse> updateUsage(
            @PathVariable Long productId,
            @Valid @RequestBody UpdateProductUsageRequest request) {
        return ApiResponse.success(dermatologyService.updateUsageDetail(productId, request));
    }

    @Operation(summary = "Cập nhật danh sách tương thích loại da (Skin Compatibility)")
    @PutMapping("/products/{productId}/skin-compatibility")
    public ApiResponse<List<AdminDermatologyService.SkinCompatibilityItemView>> updateSkinCompatibility(
            @PathVariable Long productId,
            @Valid @RequestBody List<ProductSkinCompatibilityItemRequest> items) {
        return ApiResponse.success(dermatologyService.updateSkinCompatibility(productId, items));
    }

    @Operation(summary = "Cập nhật danh sách giải quyết vấn đề da (Skin Concerns)")
    @PutMapping("/products/{productId}/skin-concerns")
    public ApiResponse<List<AdminDermatologyService.SkinConcernItemView>> updateSkinConcerns(
            @PathVariable Long productId,
            @Valid @RequestBody List<ProductSkinConcernItemRequest> items) {
        return ApiResponse.success(dermatologyService.updateSkinConcerns(productId, items));
    }
}
