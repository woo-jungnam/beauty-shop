package com.core.beautyshop.controller;

import com.core.beautyshop.dto.common.ApiResponse;
import com.core.beautyshop.dto.response.spa.BeautyServiceResponse;
import com.core.beautyshop.service.spa.BeautyServiceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
    public ResponseEntity<ApiResponse<List<BeautyServiceResponse>>> getAllServices(HttpServletRequest req) {
        return ResponseEntity.ok(ApiResponse.success(
                beautyServiceService.getAllActiveServices(), req.getRequestURI()
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BeautyServiceResponse>> getServiceById(@PathVariable Long id, HttpServletRequest req) {
        return ResponseEntity.ok(ApiResponse.success(
                beautyServiceService.getServiceById(id), req.getRequestURI()
        ));
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<ApiResponse<BeautyServiceResponse>> getServiceBySlug(@PathVariable String slug, HttpServletRequest req) {
        return ResponseEntity.ok(ApiResponse.success(
                beautyServiceService.getServiceBySlug(slug), req.getRequestURI()
        ));
    }
}
