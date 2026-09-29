package com.core.beautyshop.modules.catalog.api;

import com.core.beautyshop.modules.catalog.application.service.MediaStorageService;
import com.core.beautyshop.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/media")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminMediaController {
    private final MediaStorageService service;
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MediaStorageService.MediaView> upload(@RequestPart("file") MultipartFile file) {
        return ApiResponse.created(service.upload(file), "Image uploaded");
    }
}
