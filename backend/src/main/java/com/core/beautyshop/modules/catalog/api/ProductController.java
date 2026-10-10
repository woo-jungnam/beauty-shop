package com.core.beautyshop.modules.catalog.api;

import com.core.beautyshop.modules.catalog.application.dto.request.CreateProductRequest;
import com.core.beautyshop.modules.catalog.application.dto.request.InteractionTrackingRequest;
import com.core.beautyshop.modules.catalog.application.dto.request.ProductVariantRequest;
import com.core.beautyshop.modules.catalog.application.dto.request.UpdateProductRequest;
import com.core.beautyshop.modules.catalog.application.dto.request.ProductSearchRequest;
import com.core.beautyshop.modules.catalog.application.dto.response.ProductListResponse;
import com.core.beautyshop.modules.catalog.application.dto.response.ProductResponse;
import com.core.beautyshop.modules.catalog.application.dto.response.ProductVariantResponse;
import com.core.beautyshop.modules.catalog.application.service.ProductService;
import com.core.beautyshop.modules.catalog.application.service.ProductImageService;
import com.core.beautyshop.modules.catalog.application.service.ProductVariantService;
import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.shared.dto.PageResponse;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Quản lý sản phẩm", description = "Các API truy vấn và quản lý danh mục sản phẩm, biến thể (SKU) và hình ảnh")
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductVariantService productVariantService;
    private final ProductImageService productImageService;

    @Operation(summary = "Lấy danh sách tất cả sản phẩm (phân trang)", description = "API công khai. Chỉ sản phẩm ACTIVE chưa xóa; page bắt đầu từ 0, size mặc định 20. Gửi sort để chọn thứ tự; không khai báo sort mặc định.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductListResponse>>> getAllProducts(
           @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        Page<ProductListResponse> page = productService.getAllProducts(pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(page)));
    }

    @Operation(summary = "Lấy sản phẩm nổi bật", description = "API công khai. ACTIVE chưa xóa; size mặc định 8. Nếu trang kết quả nổi bật rỗng, API trả trang danh sách ACTIVE thông thường.")
    @GetMapping("/featured")
    public ResponseEntity<ApiResponse<PageResponse<ProductListResponse>>> getFeaturedProducts(
            @ParameterObject @PageableDefault(size = 8) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(productService.getFeaturedProducts(pageable))));
    }

    @Operation(summary = "Xem chi tiết sản phẩm theo ID", description = "API công khai. Chỉ sản phẩm ACTIVE chưa xóa; biến thể trong chi tiết chỉ gồm SKU active chưa xóa. Bao gồm thương hiệu, danh mục, ảnh và tồn kho khả dụng.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(
            @Parameter(description = "ID sản phẩm", example = "101") @PathVariable Long id) {
        ProductResponse product = productService.getProductById(id);
        return ResponseEntity.ok(ApiResponse.success(product));
    }

    @Operation(summary = "Xem chi tiết sản phẩm theo Slug", description = "API công khai. Chỉ sản phẩm ACTIVE chưa xóa, slug chính xác; cùng cấu trúc với chi tiết theo ID.")
    @GetMapping("/slug/{slug}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductBySlug(
            @Parameter(description = "Slug duy nhất của sản phẩm", example = "kem-chong-nang-la-roche-posay") @PathVariable String slug) {
        ProductResponse product = productService.getProductBySlug(slug);
        return ResponseEntity.ok(ApiResponse.success(product));
    }

    @Operation(summary = "Tìm kiếm và lọc sản phẩm", description = "API công khai, chỉ sản phẩm ACTIVE chưa xóa. Mọi bộ lọc đều tùy chọn; AND giữa các tiêu chí, OR giữa ID cùng nhóm. Lọc tại database trước phân trang, không trùng sản phẩm. Giá/onSale/inStock=true phải thỏa trên cùng SKU active chưa xóa. sort hỗ trợ id, name, createdAt, updatedAt, price hoặc minPrice, maxPrice, averageRating, totalSold; dạng sort=price,asc và có thể lặp sort. Mặc định createdAt,desc; thêm id,desc để phân trang ổn định. page >= 0, size 1..100 (mặc định 20). Tham số không hợp lệ trả 400.")
    @io.swagger.v3.oas.annotations.Parameters({
        @Parameter(name = "page", description = "Trang bắt đầu từ 0", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "integer", minimum = "0", defaultValue = "0")),
        @Parameter(name = "size", description = "Số sản phẩm mỗi trang, từ 1 đến 100", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "20")),
        @Parameter(name = "sort", description = "id/name/createdAt/updatedAt/price/minPrice/maxPrice/averageRating/totalSold,asc|desc; có thể lặp", array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "price,asc")))
    })
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PageResponse<ProductListResponse>>> searchProducts(
            @ParameterObject @Valid @ModelAttribute ProductSearchRequest request,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        Page<ProductListResponse> page = productService.searchProducts(request, pageable);
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

    @Operation(summary = "Gợi ý sản phẩm tương tự (Content-Based Item-to-Item)", description = "API công khai. Sử dụng thuật toán vector ngữ nghĩa và thuộc tính da liễu để tìm các sản phẩm tương đồng nhất.")
    @GetMapping("/{id}/similar")
    public ResponseEntity<ApiResponse<List<ProductListResponse>>> getSimilarProducts(
            @Parameter(description = "ID sản phẩm hiện tại", example = "101") @PathVariable Long id,
            @Parameter(description = "Số lượng sản phẩm gợi ý (mặc định 8, tối đa 50)") @RequestParam(defaultValue = "8") int limit) {
        List<ProductListResponse> products = productService.getSimilarProducts(id, limit);
        return ResponseEntity.ok(ApiResponse.success(products));
    }

    @Operation(summary = "Gợi ý sản phẩm dành cho bạn (Personalized Recommendation)", description = "API công khai. Dựa trên lịch sử tương tác (xem, giỏ hàng, mua) để cá nhân hóa gợi ý hoặc trả sản phẩm nổi bật/bán chạy nhất nếu là khách mới.")
    @GetMapping("/recommended-for-you")
    public ResponseEntity<ApiResponse<List<ProductListResponse>>> getRecommendedForYou(
            @Parameter(description = "Mã phiên làm việc ẩn danh của khách (Guest Session ID)") @RequestParam(required = false) String sessionId,
            @Parameter(description = "Số lượng sản phẩm gợi ý (mặc định 8, tối đa 50)") @RequestParam(defaultValue = "8") int limit) {
        Long userId = SecurityUtils.getCurrentUserIdOptional().orElse(null);
        List<ProductListResponse> products = productService.getRecommendedForYou(userId, sessionId, limit);
        return ResponseEntity.ok(ApiResponse.success(products));
    }

    @Operation(summary = "Lấy danh sách sản phẩm xả kho cận date (Clearance / Flash Sale)", description = "API công khai. Lấy các sản phẩm có lô hàng sắp hết hạn trong vòng thresholdDays ngày (mặc định 90 ngày) theo nguyên tắc FEFO. Minh bạch hạn sử dụng (HSD), số ngày còn lại và số lượng tồn xả kho.")
    @GetMapping("/expiring-soon")
    public ResponseEntity<ApiResponse<List<ProductListResponse>>> getExpiringSoonProducts(
            @Parameter(description = "Số ngày tối đa trước khi hết hạn (15 - 180)", example = "90") @RequestParam(defaultValue = "90") int thresholdDays,
            @Parameter(description = "Số lượng sản phẩm tối đa (mặc định 8, tối đa 50)", example = "8") @RequestParam(defaultValue = "8") int limit) {
        List<ProductListResponse> products = productService.getExpiringSoonProducts(thresholdDays, limit);
        return ResponseEntity.ok(ApiResponse.success(products));
    }

    @Operation(summary = "Ghi nhận hành vi tương tác sản phẩm", description = "API công khai. Thu thập các hành vi: VIEW, ADD_TO_CART, PURCHASE, SEARCH_CLICK phục vụ huấn luyện và cập nhật hồ sơ sở thích gợi ý.")
    @PostMapping("/tracking/interaction")
    public ResponseEntity<ApiResponse<Void>> trackInteraction(
            @Valid @RequestBody InteractionTrackingRequest request) {
        Long userId = SecurityUtils.getCurrentUserIdOptional().orElse(null);
        productService.recordInteraction(userId, request.getSessionId(), request.getProductId(), request.getActionType());
        return ResponseEntity.ok(ApiResponse.success(null, "Ghi nhận tương tác thành công"));
    }

    @Operation(summary = "Tạo sản phẩm mới (Admin/Staff)", description = "Yêu cầu quyền ADMIN hoặc STAFF. Slug không được trùng.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Đã tạo dữ liệu", useReturnTypeSchema = true)
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody CreateProductRequest request) {
        ProductResponse product = productService.createProduct(request);
        return ResponseEntity.status(201).body(ApiResponse.created(product, "Tạo sản phẩm thành công"));
    }

    @Operation(summary = "Cập nhật thông tin sản phẩm (Admin/Staff)", description = "Yêu cầu quyền ADMIN hoặc STAFF.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @Parameter(description = "ID sản phẩm cần sửa", example = "101") @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request) {
        ProductResponse product = productService.updateProduct(id, request);
        return ResponseEntity.ok(ApiResponse.success(product));
    }

    @Operation(summary = "Xóa sản phẩm (Admin/Staff)", description = "Xóa mềm sản phẩm khỏi hệ thống. Yêu cầu quyền ADMIN hoặc STAFF.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @Parameter(description = "ID sản phẩm cần xóa", example = "101") @PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Lấy danh sách biến thể của sản phẩm", description = "API công khai. Chỉ SKU active chưa xóa thuộc sản phẩm ACTIVE chưa xóa; không có SKU phù hợp trả [].")
    @GetMapping("/{id}/variants")
    public ResponseEntity<ApiResponse<List<ProductVariantResponse>>> getProductVariants(
            @Parameter(description = "ID sản phẩm", example = "101") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                productVariantService.getVariantsByProductId(id)));
    }

    @Operation(summary = "Thêm biến thể mới cho sản phẩm (Admin/Staff)", description = "Yêu cầu quyền ADMIN hoặc STAFF.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Đã tạo dữ liệu", useReturnTypeSchema = true)
    @PostMapping("/{id}/variants")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<ProductVariantResponse>> addProductVariant(
            @Parameter(description = "ID sản phẩm", example = "101") @PathVariable Long id,
            @Valid @RequestBody ProductVariantRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.created(
                productVariantService.addVariant(id, request), "Thêm biến thể thành công"));
    }

    @Operation(summary = "Cập nhật biến thể sản phẩm (Admin/Staff)", description = "Yêu cầu ADMIN hoặc STAFF.")
    @PutMapping("/{id}/variants/{variantId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<ProductVariantResponse>> updateProductVariant(
            @Parameter(description = "ID sản phẩm", example = "101") @PathVariable Long id,
            @Parameter(description = "ID biến thể", example = "201") @PathVariable Long variantId,
            @Valid @RequestBody com.core.beautyshop.modules.catalog.application.dto.request.ProductVariantRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                productVariantService.updateVariant(id, variantId, request)));
    }

    @Operation(summary = "Xóa biến thể sản phẩm (Admin/Staff)", description = "Yêu cầu quyền ADMIN hoặc STAFF.")
    @DeleteMapping("/{id}/variants/{variantId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<Void>> deleteProductVariant(
            @Parameter(description = "ID sản phẩm", example = "101") @PathVariable Long id,
            @Parameter(description = "ID biến thể", example = "201") @PathVariable Long variantId) {
        productVariantService.deleteVariant(id, variantId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Lấy danh sách hình ảnh của sản phẩm", description = "API công khai. Sản phẩm phải ACTIVE chưa xóa; trả ảnh chưa xóa theo displayOrder tăng dần.")
    @GetMapping("/{id}/images")
    public ResponseEntity<ApiResponse<java.util.List<com.core.beautyshop.modules.catalog.application.dto.response.ProductImageResponse>>> getProductImages(
            @Parameter(description = "ID sản phẩm", example = "101") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                productImageService.getImagesByProductId(id)));
    }

    @Operation(summary = "Thêm hình ảnh cho sản phẩm (Admin/Staff)", description = "Yêu cầu ADMIN hoặc STAFF.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Đã tạo dữ liệu", useReturnTypeSchema = true)
    @PostMapping("/{id}/images")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<com.core.beautyshop.modules.catalog.application.dto.response.ProductImageResponse>> addProductImage(
            @Parameter(description = "ID sản phẩm", example = "101") @PathVariable Long id,
            @Valid @RequestBody com.core.beautyshop.modules.catalog.application.dto.request.ProductImageRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.created(
                productImageService.addImage(id, request), "Thêm hình ảnh thành công"));
    }

    @Operation(summary = "Xóa hình ảnh của sản phẩm (Admin/Staff)")
    @DeleteMapping("/{id}/images/{imageId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<Void>> deleteProductImage(
            @Parameter(description = "ID sản phẩm", example = "101") @PathVariable Long id,
            @Parameter(description = "ID ảnh", example = "301") @PathVariable Long imageId) {
        productImageService.deleteImage(id, imageId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
