package com.core.beautyshop.controller;

import io.swagger.v3.oas.annotations.tags.Tag;


import com.core.beautyshop.dto.common.ApiResponse;
import com.core.beautyshop.dto.request.AttributeDefinitionRequest;
import com.core.beautyshop.dto.request.AttributeValueRequest;
import com.core.beautyshop.dto.response.AttributeDefinitionResponse;
import com.core.beautyshop.dto.response.AttributeValueResponse;
import com.core.beautyshop.service.product.ProductAttributeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Quản lý thuộc tính sản phẩm", description = "API quản lý thuộc tính sản phẩm")
@RestController
@RequestMapping("/api/attributes")
@RequiredArgsConstructor
public class ProductAttributeController {

    private final ProductAttributeService attributeService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AttributeDefinitionResponse>>> getAllDefinitions() {
        return ResponseEntity.ok(ApiResponse.<List<AttributeDefinitionResponse>>builder()
                .status(HttpStatus.OK.value())
                .message("Attributes retrieved successfully")
                .data(attributeService.getAllDefinitions())
                .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AttributeDefinitionResponse>> getDefinitionById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.<AttributeDefinitionResponse>builder()
                .status(HttpStatus.OK.value())
                .message("Attribute retrieved successfully")
                .data(attributeService.getDefinitionById(id))
                .build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AttributeDefinitionResponse>> createDefinition(@Valid @RequestBody AttributeDefinitionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<AttributeDefinitionResponse>builder()
                        .status(HttpStatus.CREATED.value())
                        .message("Attribute created successfully")
                        .data(attributeService.createDefinition(request))
                        .build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AttributeDefinitionResponse>> updateDefinition(
            @PathVariable Long id, @Valid @RequestBody AttributeDefinitionRequest request) {
        return ResponseEntity.ok(ApiResponse.<AttributeDefinitionResponse>builder()
                .status(HttpStatus.OK.value())
                .message("Attribute updated successfully")
                .data(attributeService.updateDefinition(id, request))
                .build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDefinition(@PathVariable Long id) {
        attributeService.deleteDefinition(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Attribute deleted successfully")
                .build());
    }

    // Values endpoints
    @GetMapping("/{id}/values")
    public ResponseEntity<ApiResponse<List<AttributeValueResponse>>> getValuesByDefinitionId(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.<List<AttributeValueResponse>>builder()
                .status(HttpStatus.OK.value())
                .message("Attribute values retrieved successfully")
                .data(attributeService.getValuesByDefinitionId(id))
                .build());
    }

    @PostMapping("/{id}/values")
    public ResponseEntity<ApiResponse<AttributeValueResponse>> addValue(
            @PathVariable Long id, @Valid @RequestBody AttributeValueRequest request) {
        request.setAttributeDefinitionId(id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<AttributeValueResponse>builder()
                        .status(HttpStatus.CREATED.value())
                        .message("Attribute value created successfully")
                        .data(attributeService.addValue(request))
                        .build());
    }

    @DeleteMapping("/values/{valueId}")
    public ResponseEntity<ApiResponse<Void>> deleteValue(@PathVariable Long valueId) {
        attributeService.deleteValue(valueId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Attribute value deleted successfully")
                .build());
    }
}