package com.core.beautyshop.modules.catalog.api;

import com.core.beautyshop.modules.catalog.application.service.ProductImageSearchService;
import com.core.beautyshop.modules.catalog.infrastructure.ImageSearchException;
import com.core.beautyshop.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Tìm kiếm sản phẩm bằng hình ảnh", description = "Tìm kiếm trực quan bằng AI/CLIP: tra cứu sản phẩm tương tự từ ảnh tải lên")
public class ProductImageSearchController {
    private final ProductImageSearchService service;

    @Operation(summary = "Tìm tối đa 5 sản phẩm còn tồn an toàn có ảnh gần giống",
            description = "Ảnh JPEG/PNG/WebP tối đa 5 MiB. Kết quả là ứng viên theo hình ảnh, có thể lọc theo danh mục, thương hiệu và ngưỡng tương đồng.")
    @PostMapping(value = "/search-by-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ProductImageSearchService.SearchResult> search(
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "brandId", required = false) Long brandId,
            @RequestParam(value = "minSimilarity", required = false, defaultValue = "0.65") Double minSimilarity) {
        return ApiResponse.success(service.search(image, categoryId, brandId, minSimilarity));
    }

    @ExceptionHandler(ImageSearchException.class)
    public ResponseEntity<ApiResponse<Void>> handle(ImageSearchException error) {
        return ResponseEntity.status(error.status()).body(ApiResponse.error(
                error.status(), "IMAGE_SEARCH_" + error.status(), error.getMessage(), null));
    }
}
