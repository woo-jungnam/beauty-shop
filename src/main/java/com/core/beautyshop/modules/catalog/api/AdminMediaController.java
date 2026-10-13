package com.core.beautyshop.modules.catalog.api;

import com.core.beautyshop.modules.catalog.application.service.MediaStorageService;
import com.core.beautyshop.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Tải lên tệp đa phương tiện (Admin)", description = "ADMIN hoặc STAFF tải tệp ảnh và nhận metadata, không tự gắn vào sản phẩm")
@RestController
@RequestMapping("/api/v1/admin/media")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
@RequiredArgsConstructor
public class AdminMediaController {
    private final MediaStorageService service;

    @Operation(summary = "Tải tệp hình ảnh lên máy chủ", description = "ADMIN hoặc CATALOG_STAFF. Gửi multipart/form-data với part file. Nhận JPEG, PNG, WEBP, GIF, AVIF hoặc BMP, tối đa 10 MiB và còn chịu giới hạn multipart của máy chủ; kiểm tra chữ ký ảnh, không nhận SVG. Sau upload dùng url để gắn ảnh vào sản phẩm; upload không tự gắn ảnh.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Ảnh đã lưu, trả metadata và URL tương đối", useReturnTypeSchema = true)
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MediaStorageService.MediaView> upload(@Parameter(description = "Tệp ảnh nhị phân, tối đa 10 MiB", required = true) @RequestParam("file") MultipartFile file) {
        return ApiResponse.created(service.upload(file), "Image uploaded");
    }
}
