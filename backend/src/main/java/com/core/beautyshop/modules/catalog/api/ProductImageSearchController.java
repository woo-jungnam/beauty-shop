package com.core.beautyshop.modules.catalog.api;

import com.core.beautyshop.modules.catalog.application.service.ProductImageSearchService;
import com.core.beautyshop.modules.catalog.infrastructure.ImageSearchException;
import com.core.beautyshop.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductImageSearchController {
    private final ProductImageSearchService service;

    @Operation(summary = "Tìm tối đa 5 sản phẩm còn tồn an toàn có ảnh gần giống",
            description = "Ảnh JPEG/PNG/WebP tối đa 5 MiB. Kết quả là ứng viên theo hình ảnh, chưa xác nhận SKU hay công dụng.")
    @PostMapping(value = "/search-by-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ProductImageSearchService.SearchResult> search(@RequestParam("image") MultipartFile image) {
        return ApiResponse.success(service.search(image));
    }

    @ExceptionHandler(ImageSearchException.class)
    public ResponseEntity<ApiResponse<Void>> handle(ImageSearchException error) {
        return ResponseEntity.status(error.status()).body(ApiResponse.error(
                error.status(), "IMAGE_SEARCH_" + error.status(), error.getMessage(), null));
    }
}
