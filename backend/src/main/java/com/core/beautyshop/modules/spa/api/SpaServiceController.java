package com.core.beautyshop.modules.spa.api;

import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.modules.spa.application.dto.response.BeautyServiceResponse;
import com.core.beautyshop.modules.spa.application.service.BeautyServiceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Dịch vụ Spa", description = "API công khai cho các dịch vụ Spa và Làm đẹp")
@RestController
@RequestMapping("/api/v1/spa/services")
@RequiredArgsConstructor
public class SpaServiceController {

    private final BeautyServiceService beautyServiceService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<BeautyServiceResponse>>> getAllServices() {
        return ResponseEntity.ok(ApiResponse.success(
                beautyServiceService.getAllActiveServices()
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BeautyServiceResponse>> getServiceById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                beautyServiceService.getServiceById(id)
        ));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<BeautyServiceResponse>> getServiceBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(
                beautyServiceService.getServiceBySlug(slug)
        ));
    }
}
