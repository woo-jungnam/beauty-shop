package com.core.beautyshop.modules.catalog.api;

import com.core.beautyshop.modules.catalog.application.service.AdminIngredientService;
import com.core.beautyshop.shared.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/ingredients")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminIngredientController {
    private final AdminIngredientService service;
    @GetMapping public ApiResponse<PageResponse<AdminIngredientService.IngredientView>> list(Pageable pageable) { return ApiResponse.success(PageResponse.of(service.list(pageable))); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public ApiResponse<AdminIngredientService.IngredientView> create(@RequestBody AdminIngredientService.IngredientCommand command) { return ApiResponse.created(service.save(null, command), "Ingredient created"); }
    @PutMapping("/{id}") public ApiResponse<AdminIngredientService.IngredientView> update(@PathVariable Long id, @RequestBody AdminIngredientService.IngredientCommand command) { return ApiResponse.success(service.save(id, command)); }
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable Long id) { service.delete(id); return ApiResponse.success(null); }
    @GetMapping("/products/{productId}") public ApiResponse<List<AdminIngredientService.ProductIngredientView>> mappings(@PathVariable Long productId) { return ApiResponse.success(service.productIngredients(productId)); }
    @PutMapping("/products/{productId}") public ApiResponse<List<AdminIngredientService.ProductIngredientView>> replace(@PathVariable Long productId, @RequestBody List<AdminIngredientService.ProductIngredientCommand> commands) { return ApiResponse.success(service.replaceProductIngredients(productId, commands)); }
}
