package com.core.beautyshop.modules.catalog.api;

import com.core.beautyshop.modules.catalog.application.dto.response.ProductListResponse;
import com.core.beautyshop.modules.catalog.application.dto.response.ProductResponse;
import com.core.beautyshop.modules.catalog.application.dto.response.ProductVariantResponse;
import com.core.beautyshop.modules.catalog.application.dto.response.ProductImageResponse;
import com.core.beautyshop.modules.catalog.application.dto.request.*;
import com.core.beautyshop.modules.catalog.application.service.ProductService;
import com.core.beautyshop.modules.catalog.application.service.ProductVariantService;
import com.core.beautyshop.modules.catalog.application.service.ProductImageService;
import com.core.beautyshop.shared.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/products")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminProductController {
    private final ProductService productService;
    private final ProductVariantService productVariantService;
    private final ProductImageService productImageService;
    @GetMapping
    public ApiResponse<PageResponse<ProductListResponse>> list(Pageable pageable) {
        return ApiResponse.success(PageResponse.of(productService.getAllProductsForAdmin(pageable)));
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        return ApiResponse.created(productService.createProduct(request), "Product created");
    }
    @PutMapping("/{id}")
    public ApiResponse<ProductResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest request) {
        return ApiResponse.success(productService.updateProduct(id, request));
    }
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) { productService.deleteProduct(id); return ApiResponse.success(null); }
    @PostMapping("/{id}/variants") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductVariantResponse> addVariant(@PathVariable Long id, @Valid @RequestBody ProductVariantRequest request) {
        return ApiResponse.created(productVariantService.addVariant(id, request), "Product variant created");
    }
    @PutMapping("/{id}/variants/{variantId}")
    public ApiResponse<ProductVariantResponse> updateVariant(@PathVariable Long id, @PathVariable Long variantId,
                                                              @Valid @RequestBody ProductVariantRequest request) {
        return ApiResponse.success(productVariantService.updateVariant(id, variantId, request));
    }
    @DeleteMapping("/{id}/variants/{variantId}")
    public ApiResponse<Void> deleteVariant(@PathVariable Long id, @PathVariable Long variantId) {
        productVariantService.deleteVariant(id, variantId); return ApiResponse.success(null);
    }
    @PostMapping("/{id}/images") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductImageResponse> addImage(@PathVariable Long id, @Valid @RequestBody ProductImageRequest request) {
        return ApiResponse.created(productImageService.addImage(id, request), "Product image created");
    }
    @DeleteMapping("/{id}/images/{imageId}")
    public ApiResponse<Void> deleteImage(@PathVariable Long id, @PathVariable Long imageId) {
        productImageService.deleteImage(id, imageId); return ApiResponse.success(null);
    }
}
