package com.core.beautyshop.modules.catalog.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.shared.dto.PageResponse;
import com.core.beautyshop.modules.catalog.application.dto.request.CreateProductRequest;
import com.core.beautyshop.modules.catalog.application.dto.request.UpdateProductRequest;
import com.core.beautyshop.modules.catalog.application.dto.response.ProductListResponse;
import com.core.beautyshop.modules.catalog.application.dto.response.ProductResponse;
import com.core.beautyshop.modules.catalog.application.service.ProductService;
import com.core.beautyshop.modules.catalog.application.service.ProductImageService;
import com.core.beautyshop.modules.catalog.application.service.ProductVariantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Quản lý sản phẩm", description = "Các API truy vấn và quản lý danh mục sản phẩm, biến thể (SKU) và hình ảnh")
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductVariantService productVariantService;
    private final ProductImageService productImageService;

    @Operation(summary = "Lấy danh sách tất cả sản phẩm (phân trang)", description = "Hỗ trợ phân trang và sắp xếp mặc định theo ID giảm dần.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductListResponse>>> getAllProducts(
           @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        Page<ProductListResponse> page = productService.getAllProducts(pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(page)));
    }

    @Operation(summary = "Xem chi tiết sản phẩm theo ID", description = "Lấy đầy đủ thông tin sản phẩm bao gồm thương hiệu, danh mục, biến thể và hình ảnh.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(
            @Parameter(description = "ID sản phẩm", example = "101") @PathVariable Long id) {
        ProductResponse product = productService.getProductById(id);
        return ResponseEntity.ok(ApiResponse.success(product));
    }

    @Operation(summary = "Xem chi tiết sản phẩm theo Slug", description = "Sử dụng cho Frontend trang chi tiết sản phẩm chuẩn SEO.")
    @GetMapping("/slug/{slug}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductBySlug(
            @Parameter(description = "Slug duy nhất của sản phẩm", example = "kem-chong-nang-la-roche-posay") @PathVariable String slug) {
        ProductResponse product = productService.getProductBySlug(slug);
        return ResponseEntity.ok(ApiResponse.success(product));
    }

    @Operation(summary = "Tìm kiếm sản phẩm theo từ khóa", description = "Tìm kiếm không phân biệt hoa thường theo tên sản phẩm.")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PageResponse<ProductListResponse>>> searchProducts(
            @Parameter(description = "Từ khóa tìm kiếm", example = "chống nắng") @RequestParam String keyword,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        Page<ProductListResponse> page = productService.searchProducts(keyword, pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(page)));
    }

    @Operation(summary = "Lấy danh sách sản phẩm theo danh mục", description = "Lọc danh sách sản phẩm thuộc một danh mục cụ thể.")
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<ApiResponse<PageResponse<ProductListResponse>>> getProductsByCategory(
            @Parameter(description = "ID danh mục", example = "1") @PathVariable Long categoryId,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        Page<ProductListResponse> page = productService.getProductsByCategory(categoryId, pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(page)));
    }

    @Operation(summary = "Lấy danh sách sản phẩm theo thương hiệu", description = "Lọc danh sách sản phẩm thuộc một thương hiệu cụ thể.")
    @GetMapping("/brand/{brandId}")
    public ResponseEntity<ApiResponse<PageResponse<ProductListResponse>>> getProductsByBrand(
            @Parameter(description = "ID thương hiệu", example = "1") @PathVariable Long brandId,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        Page<ProductListResponse> page = productService.getProductsByBrand(brandId, pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(page)));
    }

    @Operation(summary = "Tạo sản phẩm mới (Admin)", description = "Yêu cầu quyền ADMIN. Slug không được trùng.")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody CreateProductRequest request) {
        ProductResponse product = productService.createProduct(request);
        return ResponseEntity.status(201).body(ApiResponse.created(product, "Tạo sản phẩm thành công"));
    }

    @Operation(summary = "Cập nhật thông tin sản phẩm (Admin)", description = "Yêu cầu quyền ADMIN.")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @Parameter(description = "ID sản phẩm cần sửa", example = "101") @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request) {
        ProductResponse product = productService.updateProduct(id, request);
        return ResponseEntity.ok(ApiResponse.success(product));
    }

    @Operation(summary = "Xóa sản phẩm (Admin)", description = "Xóa mềm sản phẩm khỏi hệ thống. Yêu cầu quyền ADMIN.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @Parameter(description = "ID sản phẩm cần xóa", example = "101") @PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Lấy danh sách biến thể của sản phẩm", description = "Lấy tất cả các SKU biến thể (màu sắc, dung tích, giá) của sản phẩm.")
    @GetMapping("/{id}/variants")
    public ResponseEntity<ApiResponse<java.util.List<com.core.beautyshop.modules.catalog.application.dto.response.ProductVariantResponse>>> getProductVariants(
            @Parameter(description = "ID sản phẩm", example = "101") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                productVariantService.getVariantsByProductId(id)));
    }

    @Operation(summary = "Thêm biến thể mới cho sản phẩm (Admin)", description = "Yêu cầu quyền ADMIN.")
    @PostMapping("/{id}/variants")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<com.core.beautyshop.modules.catalog.application.dto.response.ProductVariantResponse>> addProductVariant(
            @Parameter(description = "ID sản phẩm", example = "101") @PathVariable Long id,
            @Valid @RequestBody com.core.beautyshop.modules.catalog.application.dto.request.ProductVariantRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.created(
                productVariantService.addVariant(id, request), "Thêm biến thể thành công"));
    }

    @Operation(summary = "Cập nhật biến thể sản phẩm (Admin)", description = "Yêu cầu quyền ADMIN.")
    @PutMapping("/{id}/variants/{variantId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<com.core.beautyshop.modules.catalog.application.dto.response.ProductVariantResponse>> updateProductVariant(
            @Parameter(description = "ID sản phẩm", example = "101") @PathVariable Long id,
            @Parameter(description = "ID biến thể", example = "201") @PathVariable Long variantId,
            @Valid @RequestBody com.core.beautyshop.modules.catalog.application.dto.request.ProductVariantRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                productVariantService.updateVariant(id, variantId, request)));
    }

    @Operation(summary = "Xóa biến thể sản phẩm (Admin)", description = "Yêu cầu quyền ADMIN.")
    @DeleteMapping("/{id}/variants/{variantId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProductVariant(
            @Parameter(description = "ID sản phẩm", example = "101") @PathVariable Long id,
            @Parameter(description = "ID biến thể", example = "201") @PathVariable Long variantId) {
        productVariantService.deleteVariant(id, variantId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Lấy danh sách hình ảnh của sản phẩm")
    @GetMapping("/{id}/images")
    public ResponseEntity<ApiResponse<java.util.List<com.core.beautyshop.modules.catalog.application.dto.response.ProductImageResponse>>> getProductImages(
            @Parameter(description = "ID sản phẩm", example = "101") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                productImageService.getImagesByProductId(id)));
    }

    @Operation(summary = "Thêm hình ảnh cho sản phẩm (Admin)")
    @PostMapping("/{id}/images")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<com.core.beautyshop.modules.catalog.application.dto.response.ProductImageResponse>> addProductImage(
            @Parameter(description = "ID sản phẩm", example = "101") @PathVariable Long id,
            @Valid @RequestBody com.core.beautyshop.modules.catalog.application.dto.request.ProductImageRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.created(
                productImageService.addImage(id, request), "Thêm hình ảnh thành công"));
    }

    @Operation(summary = "Xóa hình ảnh của sản phẩm (Admin)")
    @DeleteMapping("/{id}/images/{imageId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProductImage(
            @Parameter(description = "ID sản phẩm", example = "101") @PathVariable Long id,
            @Parameter(description = "ID ảnh", example = "301") @PathVariable Long imageId) {
        productImageService.deleteImage(id, imageId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
