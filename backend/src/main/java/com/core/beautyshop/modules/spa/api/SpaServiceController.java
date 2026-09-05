package com.core.beautyshop.modules.spa.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.core.beautyshop.modules.spa.application.dto.response.BeautyServiceResponse;
import com.core.beautyshop.modules.spa.application.service.BeautyServiceService;
import com.core.beautyshop.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Dịch vụ Spa", description = "API công khai cho các dịch vụ Spa và Làm đẹp")
@RestController
@RequestMapping("/api/v1/spa/services")
@RequiredArgsConstructor
public class SpaServiceController {

    private final BeautyServiceService beautyServiceService;

    @Operation(summary = "Lấy danh sách tất cả các dịch vụ Spa đang hoạt động")
    @GetMapping
    public ResponseEntity<ApiResponse<List<BeautyServiceResponse>>> getAllServices() {
        return ResponseEntity.ok(ApiResponse.success(
                beautyServiceService.getAllActiveServices()
        ));
    }

    @Operation(summary = "Xem chi tiết dịch vụ Spa theo ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BeautyServiceResponse>> getServiceById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                beautyServiceService.getServiceById(id)
        ));
    }

    @Operation(summary = "Xem chi tiết dịch vụ Spa theo Slug")
    @GetMapping("/slug/{slug}")
    public ResponseEntity<ApiResponse<BeautyServiceResponse>> getServiceBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(
                beautyServiceService.getServiceBySlug(slug)
        ));
    }
}
