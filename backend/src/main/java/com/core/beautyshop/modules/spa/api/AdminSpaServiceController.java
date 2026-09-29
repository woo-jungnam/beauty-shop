package com.core.beautyshop.modules.spa.api;

import com.core.beautyshop.modules.spa.application.dto.response.*;
import com.core.beautyshop.modules.spa.application.service.AdminSpaCatalogService;
import com.core.beautyshop.shared.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/spa")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSpaServiceController {
    private final AdminSpaCatalogService service;
    @GetMapping("/services") public ApiResponse<PageResponse<BeautyServiceResponse>> services(Pageable pageable) { return ApiResponse.success(PageResponse.of(service.services(pageable))); }
    @PostMapping("/services") @ResponseStatus(HttpStatus.CREATED) public ApiResponse<BeautyServiceResponse> createService(@RequestBody AdminSpaCatalogService.ServiceCommand command) { return ApiResponse.created(service.saveService(null, command), "Spa service created"); }
    @PutMapping("/services/{id}") public ApiResponse<BeautyServiceResponse> updateService(@PathVariable Long id, @RequestBody AdminSpaCatalogService.ServiceCommand command) { return ApiResponse.success(service.saveService(id, command)); }
    @DeleteMapping("/services/{id}") public ApiResponse<Void> deleteService(@PathVariable Long id) { service.deleteService(id); return ApiResponse.success(null); }
    @GetMapping("/categories") public ApiResponse<PageResponse<AdminSpaCatalogService.CategoryView>> categories(Pageable pageable) { return ApiResponse.success(PageResponse.of(service.categories(pageable))); }
    @PostMapping("/categories") @ResponseStatus(HttpStatus.CREATED) public ApiResponse<AdminSpaCatalogService.CategoryView> createCategory(@RequestBody AdminSpaCatalogService.CategoryCommand command) { return ApiResponse.created(service.saveCategory(null, command), "Spa category created"); }
    @PutMapping("/categories/{id}") public ApiResponse<AdminSpaCatalogService.CategoryView> updateCategory(@PathVariable Long id, @RequestBody AdminSpaCatalogService.CategoryCommand command) { return ApiResponse.success(service.saveCategory(id, command)); }
    @DeleteMapping("/categories/{id}") public ApiResponse<Void> deleteCategory(@PathVariable Long id) { service.deleteCategory(id); return ApiResponse.success(null); }
    @GetMapping("/packages") public ApiResponse<List<ServicePackageResponse>> packages() { return ApiResponse.success(service.packages()); }
    @PostMapping("/packages") @ResponseStatus(HttpStatus.CREATED) public ApiResponse<ServicePackageResponse> createPackage(@RequestBody AdminSpaCatalogService.PackageCommand command) { return ApiResponse.created(service.savePackage(null, command), "Spa package created"); }
    @PutMapping("/packages/{id}") public ApiResponse<ServicePackageResponse> updatePackage(@PathVariable Long id, @RequestBody AdminSpaCatalogService.PackageCommand command) { return ApiResponse.success(service.savePackage(id, command)); }
    @DeleteMapping("/packages/{id}") public ApiResponse<Void> deletePackage(@PathVariable Long id) { service.deletePackage(id); return ApiResponse.success(null); }
}
