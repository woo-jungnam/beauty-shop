package com.core.beautyshop.modules.order.api;

import com.core.beautyshop.modules.catalog.application.service.MediaStorageService;
import com.core.beautyshop.modules.order.application.service.ShipperProfileService;
import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Tài xế giao hàng (Shipper)", description = "Các API dành riêng cho shipper: nhận đơn, cập nhật tiến độ, chụp ảnh xác nhận giao hàng và hồ sơ tài xế")
@RestController
@RequestMapping("/api/v1/shipper/profile")
@PreAuthorize("hasRole('SHIPPER')")
@RequiredArgsConstructor
public class ShipperProfileController {

    private final ShipperProfileService shipperProfileService;
    private final MediaStorageService mediaStorageService;

    @Operation(summary = "Xem thông tin vận hành tài xế")
    @GetMapping
    public ApiResponse<ShipperProfileService.View> getProfile() {
        return ApiResponse.success(shipperProfileService.getProfile(SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "Cập nhật thông tin vận hành tài xế")
    @PutMapping
    public ApiResponse<ShipperProfileService.View> updateProfile(
            @Valid @RequestBody ShipperProfileService.UpdateRequest request) {
        return ApiResponse.success(
                shipperProfileService.updateProfile(SecurityUtils.getCurrentUserId(), request),
                "Đã cập nhật thông tin tài xế"
        );
    }

    @Operation(summary = "Tải lên ảnh đại diện tài xế")
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MediaStorageService.MediaView> uploadAvatar(@RequestPart("file") MultipartFile file) {
        return ApiResponse.success(mediaStorageService.upload(file));
    }
}

