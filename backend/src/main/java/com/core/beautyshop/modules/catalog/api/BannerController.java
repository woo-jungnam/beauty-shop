package com.core.beautyshop.modules.catalog.api;

import com.core.beautyshop.modules.catalog.application.dto.response.BannerResponse;
import com.core.beautyshop.modules.catalog.application.service.BannerService;
import com.core.beautyshop.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Banner quảng cáo công khai", description = "API lấy danh sách banner đang hoạt động cho trang chủ và ứng dụng")
@RestController
@RequestMapping("/api/v1/banners")
@RequiredArgsConstructor
public class BannerController {

    private final BannerService bannerService;

    @Operation(summary = "Lấy danh sách banner đang hoạt động", description = "Lấy banner active theo vị trí (HERO_SLIDE, HERO_SIDE...) hoặc tất cả")
    @GetMapping
    public ResponseEntity<ApiResponse<List<BannerResponse>>> getActiveBanners(
            @RequestParam(required = false) String position) {
        return ResponseEntity.ok(ApiResponse.success(bannerService.getActiveBanners(position)));
    }

    @Operation(summary = "Xem chi tiết banner theo ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BannerResponse>> getBannerById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(bannerService.getBannerById(id)));
    }
}
