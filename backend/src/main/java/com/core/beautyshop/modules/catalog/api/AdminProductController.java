package com.core.beautyshop.modules.catalog.api;

import com.core.beautyshop.modules.catalog.application.dto.response.ProductListResponse;
import com.core.beautyshop.modules.catalog.application.dto.response.ProductResponse;
import com.core.beautyshop.modules.catalog.application.dto.response.ProductVariantResponse;
import com.core.beautyshop.modules.catalog.application.dto.response.ProductImageResponse;
import com.core.beautyshop.modules.catalog.application.dto.request.*;
import com.core.beautyshop.modules.catalog.application.service.ProductService;
import com.core.beautyshop.modules.catalog.application.service.ProductAttributeService;
import com.core.beautyshop.modules.catalog.application.dto.response.AttributeValueResponse;
import java.util.List;
import com.core.beautyshop.modules.catalog.application.service.ProductVariantService;
import com.core.beautyshop.modules.catalog.application.service.ProductImageService;
import com.core.beautyshop.shared.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Quản lý sản phẩm & Biến thể (Admin)", description = "API dành cho ADMIN hoặc STAFF quản trị sản phẩm, SKU và thư viện ảnh")
@RestController
@RequestMapping("/api/v1/admin/products")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
@RequiredArgsConstructor
public class AdminProductController {
    private final ProductService productService;
    private final ProductVariantService productVariantService;
    private final ProductImageService productImageService;
    private final ProductAttributeService productAttributeService;

    @Operation(summary = "Thay toàn bộ thuộc tính sản phẩm và SKU", description = "[] gỡ tất cả; lưu nguyên tử, không merge")
    @PutMapping("/{id}/attributes")
    public ApiResponse<List<AttributeValueResponse>> replaceAttributes(
            @PathVariable Long id, @Valid @RequestBody List<@Valid ProductAttributeRequest> requests) {
        return ApiResponse.success(productAttributeService.replaceProductValues(id, requests));
    }

    @Operation(summary = "Lấy danh sách sản phẩm quản trị (phân trang)", description = "ADMIN hoặc CATALOG_STAFF. Sản phẩm chưa xóa thuộc mọi trạng thái; khác với danh sách public chỉ ACTIVE. page bắt đầu từ 0.")
    @GetMapping
    public ApiResponse<PageResponse<ProductListResponse>> list(@ParameterObject Pageable pageable) {
        return ApiResponse.success(PageResponse.of(productService.getAllProductsForAdmin(pageable)));
    }

    @Operation(summary = "Xem chi tiết sản phẩm theo ID")
    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(productService.getProductByIdForAdmin(id));
    }

    @Operation(summary = "Tạo sản phẩm mới")
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        return ApiResponse.created(productService.createProduct(request), "Product created");
    }

    @Operation(summary = "Cập nhật thông tin sản phẩm")
    @PutMapping("/{id}")
    public ApiResponse<ProductResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest request) {
        return ApiResponse.success(productService.updateProduct(id, request));
    }

    @Operation(summary = "Xóa sản phẩm (Soft delete)")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ApiResponse.success(null);
    }

    @Operation(summary = "Thêm biến thể SKU mới cho sản phẩm")
    @PostMapping("/{id}/variants") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductVariantResponse> addVariant(@PathVariable Long id, @Valid @RequestBody ProductVariantRequest request) {
        return ApiResponse.created(productVariantService.addVariant(id, request), "Product variant created");
    }

    @Operation(summary = "Cập nhật biến thể SKU", description = "ADMIN hoặc CATALOG_STAFF. sku/variantName/price bắt buộc; discountPrice/volume/color/barcode null sẽ xóa giá trị, cờ null giữ nguyên. variantId phải thuộc product id.")
    @PutMapping("/{id}/variants/{variantId}")
    public ApiResponse<ProductVariantResponse> updateVariant(@PathVariable Long id, @PathVariable Long variantId,
                                                              @Valid @RequestBody ProductVariantRequest request) {
        return ApiResponse.success(productVariantService.updateVariant(id, variantId, request));
    }

    @Operation(summary = "Xóa biến thể SKU")
    @DeleteMapping("/{id}/variants/{variantId}")
    public ApiResponse<Void> deleteVariant(@PathVariable Long id, @PathVariable Long variantId) {
        productVariantService.deleteVariant(id, variantId);
        return ApiResponse.success(null);
    }

    @Operation(summary = "Thêm ảnh vào thư viện sản phẩm", description = "ADMIN hoặc CATALOG_STAFF. Gửi JSON imageUrl, không upload nhị phân; upload trước qua admin/media/upload. Không tự bỏ cờ primary của các ảnh khác.")
    @PostMapping("/{id}/images") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductImageResponse> addImage(@PathVariable Long id, @Valid @RequestBody ProductImageRequest request) {
        return ApiResponse.created(productImageService.addImage(id, request), "Product image created");
    }

    @Operation(summary = "Xóa ảnh khỏi thư viện sản phẩm")
    @DeleteMapping("/{id}/images/{imageId}")
    public ApiResponse<Void> deleteImage(@PathVariable Long id, @PathVariable Long imageId) {
        productImageService.deleteImage(id, imageId);
        return ApiResponse.success(null);
    }
}
