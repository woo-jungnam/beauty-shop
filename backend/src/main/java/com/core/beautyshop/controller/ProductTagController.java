package com.core.beautyshop.controller;

import io.swagger.v3.oas.annotations.tags.Tag;


import com.core.beautyshop.dto.common.ApiResponse;
import com.core.beautyshop.dto.request.TagRequest;
import com.core.beautyshop.dto.response.TagResponse;
import com.core.beautyshop.service.product.ProductTagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Quản lý thẻ sản phẩm", description = "API quản lý thẻ sản phẩm")
@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class ProductTagController {

    private final ProductTagService tagService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TagResponse>>> getAllTags() {
        return ResponseEntity.ok(ApiResponse.<List<TagResponse>>builder()
                .status(HttpStatus.OK.value())
                .message("Tags retrieved successfully")
                .data(tagService.getAllTags())
                .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TagResponse>> getTagById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.<TagResponse>builder()
                .status(HttpStatus.OK.value())
                .message("Tag retrieved successfully")
                .data(tagService.getTagById(id))
                .build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TagResponse>> createTag(@Valid @RequestBody TagRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<TagResponse>builder()
                        .status(HttpStatus.CREATED.value())
                        .message("Tag created successfully")
                        .data(tagService.createTag(request))
                        .build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTag(@PathVariable Long id) {
        tagService.deleteTag(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Tag deleted successfully")
                .build());
    }
}