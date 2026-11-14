package com.core.beautyshop.modules.catalog.api;

import com.core.beautyshop.modules.catalog.infrastructure.ImageSearchClient;
import com.core.beautyshop.modules.catalog.infrastructure.ImageSearchException;
import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.shared.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/image-search")
@RequiredArgsConstructor
@Tag(name = "Quản trị Tìm kiếm bằng Hình ảnh", description = "Chỉ ADMIN: Xem trạng thái, đồng bộ toàn bộ chỉ mục Qdrant và kiểm tra trực quan")
@PreAuthorize("hasRole('ADMIN')")
public class AdminImageSearchController {

    private final ImageSearchClient client;

    @GetMapping("/status")
    @Operation(summary = "Xem trạng thái kết nối & tổng số vector ảnh trong Qdrant")
    public ApiResponse<Map<String, Object>> getStatus() {
        return ApiResponse.success(client.getStatus());
    }

    @PostMapping("/reindex")
    @Operation(summary = "Kích hoạt quét toàn bộ MySQL catalog và nạp lại vào Qdrant")
    public ApiResponse<Map<String, Object>> triggerReindex() {
        return ApiResponse.success(client.triggerReindex(), "Đã khởi động tiến trình đồng bộ toàn bộ chỉ mục hình ảnh.");
    }

    @GetMapping("/reindex-status")
    @Operation(summary = "Theo dõi tiến độ phần trăm đồng bộ vector ảnh theo thời gian thực")
    public ApiResponse<Map<String, Object>> getReindexStatus() {
        return ApiResponse.success(client.getReindexStatus());
    }

    @PostMapping(value = "/test-search", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Thử nghiệm tìm kiếm bằng ảnh tải lên trong trang quản trị (kèm điểm cosine)")
    public ApiResponse<ImageSearchClient.Result> testSearch(@RequestParam("image") MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new BusinessException("Vui lòng tải lên một tệp hình ảnh để kiểm tra.");
        }
        if (image.getSize() > 5 * 1024 * 1024) {
            throw new BusinessException("Kích thước tệp vượt quá giới hạn 5 MB.");
        }
        try {
            byte[] bytes = image.getBytes();
            return ApiResponse.success(client.search(bytes));
        } catch (IOException e) {
            throw new BusinessException("Không thể đọc tệp hình ảnh: " + e.getMessage());
        }
    }

    @ExceptionHandler(ImageSearchException.class)
    public ResponseEntity<ApiResponse<Void>> handle(ImageSearchException error) {
        return ResponseEntity.status(error.status()).body(ApiResponse.error(
                error.status(), "IMAGE_SEARCH_" + error.status(), error.getMessage(), null));
    }
}

