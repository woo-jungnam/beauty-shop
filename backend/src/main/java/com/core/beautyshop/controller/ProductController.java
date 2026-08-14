package com.core.beautyshop.controller;

import io.swagger.v3.oas.annotations.tags.Tag;


import com.core.beautyshop.dto.common.ApiResponse;
import com.core.beautyshop.dto.common.PageResponse;
import com.core.beautyshop.dto.request.CreateProductRequest;
import com.core.beautyshop.dto.request.UpdateProductRequest;
import com.core.beautyshop.dto.response.ProductListResponse;
import com.core.beautyshop.dto.response.ProductResponse;
import com.core.beautyshop.service.product.ProductService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Quản lý sản phẩm", description = "API quản lý sản phẩm")
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final com.core.beautyshop.service.product.ProductVariantService productVariantService;
    private final com.core.beautyshop.service.product.ProductImageService productImageService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductListResponse>>> getAllProducts(
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest req) {
        Page<ProductListResponse> page = productService.getAllProducts(pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(page), req.getRequestURI()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(
            @PathVariable Long id,
            HttpServletRequest req) {
        ProductResponse product = productService.getProductById(id);
        return ResponseEntity.ok(ApiResponse.success(product, req.getRequestURI()));
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductBySlug(
            @PathVariable String slug,
            HttpServletRequest req) {
        ProductResponse product = productService.getProductBySlug(slug);
        return ResponseEntity.ok(ApiResponse.success(product, req.getRequestURI()));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PageResponse<ProductListResponse>>> searchProducts(
            @RequestParam String keyword,
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest req) {
        Page<ProductListResponse> page = productService.searchProducts(keyword, pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(page), req.getRequestURI()));
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<ApiResponse<PageResponse<ProductListResponse>>> getProductsByCategory(
            @PathVariable Long categoryId,
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest req) {
        Page<ProductListResponse> page = productService.getProductsByCategory(categoryId, pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(page), req.getRequestURI()));
    }

    @GetMapping("/brand/{brandId}")
    public ResponseEntity<ApiResponse<PageResponse<ProductListResponse>>> getProductsByBrand(
            @PathVariable Long brandId,
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest req) {
        Page<ProductListResponse> page = productService.getProductsByBrand(brandId, pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(page), req.getRequestURI()));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody CreateProductRequest request,
            HttpServletRequest req) {
        ProductResponse product = productService.createProduct(request);
        return ResponseEntity.status(201).body(ApiResponse.created(product, "Product created successfully", req.getRequestURI()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request,
            HttpServletRequest req) {
        ProductResponse product = productService.updateProduct(id, request);
        return ResponseEntity.ok(ApiResponse.success(product, req.getRequestURI()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @PathVariable Long id,
            HttpServletRequest req) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success(null, req.getRequestURI()));
    }

    // --- Variant Endpoints ---

    @GetMapping("/{id}/variants")
    public ResponseEntity<ApiResponse<java.util.List<com.core.beautyshop.dto.response.ProductVariantResponse>>> getProductVariants(
            @PathVariable Long id, HttpServletRequest req) {
        return ResponseEntity.ok(ApiResponse.success(
                productVariantService.getVariantsByProductId(id), req.getRequestURI()));
    }

    @PostMapping("/{id}/variants")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<com.core.beautyshop.dto.response.ProductVariantResponse>> addProductVariant(
            @PathVariable Long id,
            @Valid @RequestBody com.core.beautyshop.dto.request.ProductVariantRequest request,
            HttpServletRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.created(
                productVariantService.addVariant(id, request), "Variant added successfully", req.getRequestURI()));
    }

    @PutMapping("/{id}/variants/{variantId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<com.core.beautyshop.dto.response.ProductVariantResponse>> updateProductVariant(
            @PathVariable Long id,
            @PathVariable Long variantId,
            @Valid @RequestBody com.core.beautyshop.dto.request.ProductVariantRequest request,
            HttpServletRequest req) {
        return ResponseEntity.ok(ApiResponse.success(
                productVariantService.updateVariant(variantId, request), req.getRequestURI()));
    }

    @DeleteMapping("/{id}/variants/{variantId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProductVariant(
            @PathVariable Long id,
            @PathVariable Long variantId,
            HttpServletRequest req) {
        productVariantService.deleteVariant(variantId);
        return ResponseEntity.ok(ApiResponse.success(null, req.getRequestURI()));
    }

    // --- Image Endpoints ---

    @GetMapping("/{id}/images")
    public ResponseEntity<ApiResponse<java.util.List<com.core.beautyshop.dto.response.ProductImageResponse>>> getProductImages(
            @PathVariable Long id, HttpServletRequest req) {
        return ResponseEntity.ok(ApiResponse.success(
                productImageService.getImagesByProductId(id), req.getRequestURI()));
    }

    @PostMapping("/{id}/images")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<com.core.beautyshop.dto.response.ProductImageResponse>> addProductImage(
            @PathVariable Long id,
            @Valid @RequestBody com.core.beautyshop.dto.request.ProductImageRequest request,
            HttpServletRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.created(
                productImageService.addImage(id, request), "Image added successfully", req.getRequestURI()));
    }

    @DeleteMapping("/{id}/images/{imageId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProductImage(
            @PathVariable Long id,
            @PathVariable Long imageId,
            HttpServletRequest req) {
        productImageService.deleteImage(imageId);
        return ResponseEntity.ok(ApiResponse.success(null, req.getRequestURI()));
    }
}
