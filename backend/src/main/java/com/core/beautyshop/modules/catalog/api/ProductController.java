package com.core.beautyshop.modules.catalog.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.shared.dto.PageResponse;
import com.core.beautyshop.modules.catalog.application.dto.request.CreateProductRequest;
import com.core.beautyshop.modules.catalog.application.dto.request.UpdateProductRequest;
import com.core.beautyshop.modules.catalog.application.dto.response.ProductListResponse;
import com.core.beautyshop.modules.catalog.application.dto.response.ProductResponse;
import com.core.beautyshop.modules.catalog.application.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springdoc.core.annotations.ParameterObject;
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
    private final com.core.beautyshop.modules.catalog.application.service.ProductVariantService productVariantService;
    private final com.core.beautyshop.modules.catalog.application.service.ProductImageService productImageService;

    @Operation(summary = "Lấy danh sách tất cả sản phẩm (phân trang)")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductListResponse>>> getAllProducts(
           @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        Page<ProductListResponse> page = productService.getAllProducts(pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(page)));
    }

    @Operation(summary = "Xem chi tiết sản phẩm theo ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(@PathVariable Long id) {
        ProductResponse product = productService.getProductById(id);
        return ResponseEntity.ok(ApiResponse.success(product));
    }

    @Operation(summary = "Xem chi tiết sản phẩm theo Slug")
    @GetMapping("/slug/{slug}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductBySlug(@PathVariable String slug) {
        ProductResponse product = productService.getProductBySlug(slug);
        return ResponseEntity.ok(ApiResponse.success(product));
    }

    @Operation(summary = "Tìm kiếm sản phẩm theo từ khóa")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PageResponse<ProductListResponse>>> searchProducts(
            @RequestParam String keyword,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<ProductListResponse> page = productService.searchProducts(keyword, pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(page)));
    }

    @Operation(summary = "Lấy danh sách sản phẩm theo danh mục")
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<ApiResponse<PageResponse<ProductListResponse>>> getProductsByCategory(
            @PathVariable Long categoryId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<ProductListResponse> page = productService.getProductsByCategory(categoryId, pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(page)));
    }

    @Operation(summary = "Lấy danh sách sản phẩm theo thương hiệu")
    @GetMapping("/brand/{brandId}")
    public ResponseEntity<ApiResponse<PageResponse<ProductListResponse>>> getProductsByBrand(
            @PathVariable Long brandId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<ProductListResponse> page = productService.getProductsByBrand(brandId, pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(page)));
    }

    @Operation(summary = "Tạo sản phẩm mới (Admin)")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody CreateProductRequest request) {
        ProductResponse product = productService.createProduct(request);
        return ResponseEntity.status(201).body(ApiResponse.created(product, "Tạo sản phẩm thành công"));
    }

    @Operation(summary = "Cập nhật thông tin sản phẩm (Admin)")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request) {
        ProductResponse product = productService.updateProduct(id, request);
        return ResponseEntity.ok(ApiResponse.success(product));
    }

    @Operation(summary = "Xóa sản phẩm (Admin)")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Lấy danh sách biến thể của sản phẩm")
    @GetMapping("/{id}/variants")
    public ResponseEntity<ApiResponse<java.util.List<com.core.beautyshop.modules.catalog.application.dto.response.ProductVariantResponse>>> getProductVariants(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                productVariantService.getVariantsByProductId(id)));
    }

    @Operation(summary = "Thêm biến thể mới cho sản phẩm (Admin)")
    @PostMapping("/{id}/variants")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<com.core.beautyshop.modules.catalog.application.dto.response.ProductVariantResponse>> addProductVariant(
            @PathVariable Long id,
            @Valid @RequestBody com.core.beautyshop.modules.catalog.application.dto.request.ProductVariantRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.created(
                productVariantService.addVariant(id, request), "Thêm biến thể thành công"));
    }

    @Operation(summary = "Cập nhật biến thể sản phẩm (Admin)")
    @PutMapping("/{id}/variants/{variantId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<com.core.beautyshop.modules.catalog.application.dto.response.ProductVariantResponse>> updateProductVariant(
            @PathVariable Long id,
            @PathVariable Long variantId,
            @Valid @RequestBody com.core.beautyshop.modules.catalog.application.dto.request.ProductVariantRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                productVariantService.updateVariant(variantId, request)));
    }

    @Operation(summary = "Xóa biến thể sản phẩm (Admin)")
    @DeleteMapping("/{id}/variants/{variantId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProductVariant(
            @PathVariable Long id,
            @PathVariable Long variantId) {
        productVariantService.deleteVariant(variantId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Lấy danh sách hình ảnh của sản phẩm")
    @GetMapping("/{id}/images")
    public ResponseEntity<ApiResponse<java.util.List<com.core.beautyshop.modules.catalog.application.dto.response.ProductImageResponse>>> getProductImages(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                productImageService.getImagesByProductId(id)));
    }

    @Operation(summary = "Thêm hình ảnh cho sản phẩm (Admin)")
    @PostMapping("/{id}/images")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<com.core.beautyshop.modules.catalog.application.dto.response.ProductImageResponse>> addProductImage(
            @PathVariable Long id,
            @Valid @RequestBody com.core.beautyshop.modules.catalog.application.dto.request.ProductImageRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.created(
                productImageService.addImage(id, request), "Thêm hình ảnh thành công"));
    }

    @Operation(summary = "Xóa hình ảnh của sản phẩm (Admin)")
    @DeleteMapping("/{id}/images/{imageId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProductImage(
            @PathVariable Long id,
            @PathVariable Long imageId) {
        productImageService.deleteImage(imageId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
