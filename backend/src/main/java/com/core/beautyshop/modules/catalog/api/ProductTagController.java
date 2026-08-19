package com.core.beautyshop.modules.catalog.api;

import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.modules.catalog.application.dto.request.TagRequest;
import com.core.beautyshop.modules.catalog.application.dto.response.TagResponse;
import com.core.beautyshop.modules.catalog.application.service.ProductTagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Quản lý thẻ sản phẩm", description = "API quản lý thẻ sản phẩm")
@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
public class ProductTagController {

    private final ProductTagService tagService;

    @Operation(summary = "Lấy danh sách tất cả thẻ")
    @GetMapping
    public ResponseEntity<ApiResponse<List<TagResponse>>> getAllTags() {
        return ResponseEntity.ok(ApiResponse.success(tagService.getAllTags()));
    }

    @Operation(summary = "Lấy thông tin thẻ theo ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TagResponse>> getTagById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(tagService.getTagById(id)));
    }

    @Operation(summary = "Tạo thẻ sản phẩm mới (Admin)")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TagResponse>> createTag(
            @Valid @RequestBody TagRequest request) {
        TagResponse response = tagService.createTag(request);
        return ResponseEntity.status(201).body(ApiResponse.created(response, "Tạo thẻ thành công"));
    }

    @Operation(summary = "Xóa thẻ sản phẩm (Admin)")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteTag(@PathVariable Long id) {
        tagService.deleteTag(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
