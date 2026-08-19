package com.core.beautyshop.modules.catalog.api;

import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.modules.catalog.application.dto.request.AttributeDefinitionRequest;
import com.core.beautyshop.modules.catalog.application.dto.request.AttributeValueRequest;
import com.core.beautyshop.modules.catalog.application.dto.response.AttributeDefinitionResponse;
import com.core.beautyshop.modules.catalog.application.dto.response.AttributeValueResponse;
import com.core.beautyshop.modules.catalog.application.service.ProductAttributeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Quản lý thuộc tính sản phẩm", description = "API quản lý thuộc tính sản phẩm")
@RestController
@RequestMapping("/api/v1/attributes")
@RequiredArgsConstructor
public class ProductAttributeController {

    private final ProductAttributeService attributeService;

    @Operation(summary = "Lấy tất cả định nghĩa thuộc tính")
    @GetMapping
    public ResponseEntity<ApiResponse<List<AttributeDefinitionResponse>>> getAllDefinitions() {
        return ResponseEntity.ok(ApiResponse.success(attributeService.getAllDefinitions()));
    }

    @Operation(summary = "Lấy định nghĩa thuộc tính theo ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AttributeDefinitionResponse>> getDefinitionById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(attributeService.getDefinitionById(id)));
    }

    @Operation(summary = "Tạo định nghĩa thuộc tính mới (Admin)")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AttributeDefinitionResponse>> createDefinition(
            @Valid @RequestBody AttributeDefinitionRequest request) {
        AttributeDefinitionResponse response = attributeService.createDefinition(request);
        return ResponseEntity.status(201).body(ApiResponse.created(response, "Tạo thuộc tính thành công"));
    }

    @Operation(summary = "Cập nhật định nghĩa thuộc tính (Admin)")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AttributeDefinitionResponse>> updateDefinition(
            @PathVariable Long id,
            @Valid @RequestBody AttributeDefinitionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(attributeService.updateDefinition(id, request)));
    }

    @Operation(summary = "Xóa định nghĩa thuộc tính (Admin)")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteDefinition(@PathVariable Long id) {
        attributeService.deleteDefinition(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // Values endpoints
    @Operation(summary = "Lấy danh sách giá trị theo định nghĩa thuộc tính")
    @GetMapping("/{id}/values")
    public ResponseEntity<ApiResponse<List<AttributeValueResponse>>> getValuesByDefinitionId(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(attributeService.getValuesByDefinitionId(id)));
    }

    @Operation(summary = "Thêm giá trị thuộc tính (Admin)")
    @PostMapping("/{id}/values")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AttributeValueResponse>> addValue(
            @PathVariable Long id,
            @Valid @RequestBody AttributeValueRequest request) {
        AttributeValueResponse response = attributeService.addValue(id, request);
        return ResponseEntity.status(201).body(ApiResponse.created(response, "Thêm giá trị thuộc tính thành công"));
    }

    @Operation(summary = "Xóa giá trị thuộc tính (Admin)")
    @DeleteMapping("/{valueId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteValue(@PathVariable Long valueId) {
        attributeService.deleteValue(valueId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
