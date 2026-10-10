package com.core.beautyshop.modules.catalog.api;

import com.core.beautyshop.modules.catalog.application.dto.request.BannerRequest;
import com.core.beautyshop.modules.catalog.application.dto.response.BannerResponse;
import com.core.beautyshop.modules.catalog.application.service.BannerService;
import com.core.beautyshop.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Quản lý Banner (Admin)", description = "CRUD banner quảng cáo trang chủ và các phân vùng")
@RestController
@RequestMapping("/api/v1/admin/banners")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
@RequiredArgsConstructor
public class AdminBannerController {

    private final BannerService bannerService;

    @Operation(summary = "Lấy toàn bộ danh sách banner cho quản trị")
    @GetMapping
    public ResponseEntity<ApiResponse<List<BannerResponse>>> getAllBanners() {
        return ResponseEntity.ok(ApiResponse.success(bannerService.getAllBannersForAdmin()));
    }

    @Operation(summary = "Xem chi tiết banner")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BannerResponse>> getBannerById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(bannerService.getBannerById(id)));
    }

    @Operation(summary = "Tạo banner mới")
    @PostMapping
    public ResponseEntity<ApiResponse<BannerResponse>> createBanner(@Valid @RequestBody BannerRequest request) {
        BannerResponse created = bannerService.createBanner(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(created, "Đã tạo banner thành công"));
    }

    @Operation(summary = "Cập nhật banner")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BannerResponse>> updateBanner(
            @PathVariable Long id,
            @Valid @RequestBody BannerRequest request) {
        BannerResponse updated = bannerService.updateBanner(id, request);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @Operation(summary = "Xóa banner (soft-delete)")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteBanner(@PathVariable Long id) {
        bannerService.deleteBanner(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
